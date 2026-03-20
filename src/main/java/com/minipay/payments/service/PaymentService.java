package com.minipay.payments.service;

import com.minipay.common.PageResponse;
import com.minipay.common.errors.*;
import com.minipay.common.utils.FeeCalculator;
import com.minipay.merchants.domain.*;
import com.minipay.merchants.repo.*;
import com.minipay.merchants.service.MerchantService;
import com.minipay.payments.domain.Payment;
import com.minipay.payments.dto.PaymentDtos.*;
import com.minipay.payments.repo.PaymentRepository;
import com.minipay.webhooks.service.WebhookDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MerchantService merchantService;
    private final ChargeSettingRepository chargeSettingRepository;
    private final WebhookDispatchService webhookDispatchService;
    private final PaymentRateLimiter rateLimiter;

    @Transactional
    public PaymentResponse initiatePayment(InitiatePaymentRequest request, String idempotencyKey) {
        // ── 1. Idempotency check ─────────────────────────────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> {
                        if ("PENDING".equals(existing.getStatus())) {
                            return toResponse(existing);
                        }
                        throw new IdempotencyConflictException(existing.getPaymentRef().toString());
                    })
                    .orElseGet(() -> doInitiate(request, idempotencyKey));
        }
        return doInitiate(request, null);
    }

    private PaymentResponse doInitiate(InitiatePaymentRequest request, String idempotencyKey) {
        // ── 2. Rate limiting per merchant ────────────────────────────────────
        rateLimiter.checkLimit(request.getMerchantId());

        // ── 3. Load merchant and charge settings ─────────────────────────────
        Merchant merchant = merchantService.findOrThrow(request.getMerchantId());

        if (!"ACTIVE".equals(merchant.getStatus())) {
            throw new BusinessException("Merchant is not active: " + request.getMerchantId());
        }

        ChargeSetting chargeSetting = chargeSettingRepository.findByMerchant(merchant)
                .orElseThrow(() -> new BusinessException(
                        "Charge settings not configured for merchant: " + request.getMerchantId()));

        // ── 4. Compute fees ──────────────────────────────────────────────────
        FeeCalculator.FeeResult fees = FeeCalculator.compute(request.getAmount(), chargeSetting);

        // ── 5. Persist payment ───────────────────────────────────────────────
        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .merchant(merchant)
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .channel(request.getChannel())
                .status("PENDING")
                .msc(fees.getMsc())
                .vatAmount(fees.getVatAmount())
                .processorFee(fees.getProcessorFee())
                .processorVat(fees.getProcessorVat())
                .payableVat(fees.getPayableVat())
                .amountPayable(fees.getAmountPayable())
                .customerId(request.getCustomerId())
                .callbackUrl(request.getCallbackUrl() != null
                        ? request.getCallbackUrl()
                        : merchant.getCallbackUrl())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment initiated: {} for merchant: {} amount: {} {}",
                payment.getPaymentRef(), merchant.getMerchantId(),
                request.getAmount(), request.getCurrency());

        return toResponse(payment);
    }

    /**
     * Simulates a processor callback — transitions PENDING → SUCCESS|FAILED.
     * Uses pessimistic locking to prevent concurrent status transitions.
     */
    @Transactional
    public PaymentResponse simulateProcessorCallback(SimulateCallbackRequest request) {
        Payment payment = paymentRepository.findByPaymentRefForUpdate(request.getPaymentRef())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment", request.getPaymentRef().toString()));

        if (!"PENDING".equals(payment.getStatus())) {
            throw new BusinessException(
                    "Payment " + payment.getPaymentRef() + " is already in status: " + payment.getStatus());
        }

        payment.setStatus(request.getStatus());
        if ("FAILED".equals(request.getStatus())) {
            payment.setFailureReason(
                    request.getFailureReason() != null ? request.getFailureReason() : "Processor declined");
        }
        payment = paymentRepository.save(payment);

        log.info("Payment {} transitioned to {} via processor callback",
                payment.getPaymentRef(), payment.getStatus());

        // ── Dispatch webhook asynchronously ──────────────────────────────────
        webhookDispatchService.enqueue(payment);

        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentRef) {
        return toResponse(paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentRef.toString())));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listPayments(PaymentFilter filter) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(filter.getSortDir())
                        ? Sort.Direction.DESC : Sort.Direction.ASC,
                filter.getSortBy());
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<Payment> page = paymentRepository.findByFilters(
                filter.getMerchantId(),
                filter.getChannel(),
                filter.getStatus(),
                filter.getFrom(),
                filter.getTo(),
                pageable);

        return PageResponse.of(page.map(this::toResponse));
    }

    public PaymentResponse toResponse(Payment p) {
        PaymentResponse r = new PaymentResponse();
        r.setId(p.getId());
        r.setPaymentRef(p.getPaymentRef());
        r.setMerchantId(p.getMerchant().getMerchantId());
        r.setOrderId(p.getOrderId());
        r.setAmount(p.getAmount());
        r.setCurrency(p.getCurrency());
        r.setChannel(p.getChannel());
        r.setStatus(p.getStatus());
        r.setMsc(p.getMsc());
        r.setVatAmount(p.getVatAmount());
        r.setProcessorFee(p.getProcessorFee());
        r.setProcessorVat(p.getProcessorVat());
        r.setPayableVat(p.getPayableVat());
        r.setAmountPayable(p.getAmountPayable());
        r.setCustomerId(p.getCustomerId());
        r.setCallbackUrl(p.getCallbackUrl());
        r.setFailureReason(p.getFailureReason());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}
