package com.minipay.webhooks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.payments.domain.Payment;
import com.minipay.webhooks.domain.WebhookEvent;
import com.minipay.webhooks.repo.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enqueues webhook events for async dispatch. Actual delivery is handled
 * by {@link WebhookRetryScheduler} with exponential backoff.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatchService {

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Builds the canonical payload and persists a WebhookEvent with PENDING status.
     * Runs in a new transaction so the event is committed even if the caller rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(Payment payment) {
        String callbackUrl = payment.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.debug("No callback URL for payment {}, skipping webhook", payment.getPaymentRef());
            return;
        }

        String payload = buildPayload(payment);

        WebhookEvent event = WebhookEvent.builder()
                .merchant(payment.getMerchant())
                .payment(payment)
                .payload(payload)
                .targetUrl(callbackUrl)
                .nextRetryAt(Instant.now()) // eligible immediately
                .build();

        webhookEventRepository.save(event);
        log.info("Webhook event enqueued for payment: {}", payment.getPaymentRef());
    }

    private String buildPayload(Payment payment) {
        // Ordered map for canonical JSON (field order is stable for HMAC)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentRef",   payment.getPaymentRef().toString());
        body.put("orderId",      payment.getOrderId());
        body.put("status",       payment.getStatus());
        body.put("amount",       payment.getAmount());
        body.put("currency",     payment.getCurrency());
        body.put("msc",          payment.getMsc());
        body.put("vatAmount",    payment.getVatAmount());
        body.put("processorFee", payment.getProcessorFee());
        body.put("processorVat", payment.getProcessorVat());
        body.put("amountPayable",payment.getAmountPayable());
        body.put("timestamp",    Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }
}
