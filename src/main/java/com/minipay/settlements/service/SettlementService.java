package com.minipay.settlements.service;

import com.minipay.common.PageResponse;
import com.minipay.common.errors.ResourceNotFoundException;
import com.minipay.merchants.domain.Merchant;
import com.minipay.merchants.repo.MerchantRepository;
import com.minipay.payments.domain.Payment;
import com.minipay.payments.repo.PaymentRepository;
import com.minipay.settlements.domain.SettlementBatch;
import com.minipay.settlements.domain.SettlementItem;
import com.minipay.settlements.dto.SettlementDtos.*;
import com.minipay.settlements.repo.SettlementBatchRepository;
import com.minipay.settlements.repo.SettlementItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final SettlementBatchRepository batchRepository;
    private final SettlementItemRepository itemRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Daily automated settlement job — runs at 01:00 every day.
     * Settles the previous calendar day.
     */
    @Scheduled(cron = "${app.settlement.cron:0 0 1 * * *}")
    @SchedulerLock(name = "dailySettlement", lockAtMostFor = "PT55M", lockAtLeastFor = "PT5M")
    @Transactional
    public void runDailySettlement() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1);
        log.info("Running daily settlement for period: {}", yesterday);
        GenerateResult result = generateSettlements(yesterday, yesterday);
        log.info("Daily settlement complete: {}", result);
    }

    /**
     * On-demand settlement generation for a given date range.
     * Idempotent: skips merchants already settled for the period.
     */
    @Transactional
    public GenerateResult generateSettlements(LocalDate from, LocalDate to) {
        List<Merchant> activeMerchants = merchantRepository
                .findByFilters("ACTIVE", null, Pageable.unpaged()).getContent();

        int created = 0, skipped = 0, totalTxns = 0;

        for (Merchant merchant : activeMerchants) {
            // Idempotency guard — skip if batch already exists for this period
            if (batchRepository.existsByMerchantIdAndPeriodStartAndPeriodEnd(
                    merchant.getId(), from, to)) {
                log.debug("Skipping settlement for merchant {} — batch already exists", merchant.getMerchantId());
                skipped++;
                continue;
            }

            Instant startInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant endInstant   = to.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

            List<Payment> payments = paymentRepository.findSuccessfulForSettlement(
                    merchant.getMerchantId(), startInstant, endInstant);

            if (payments.isEmpty()) {
                skipped++;
                continue;
            }

            SettlementBatch batch = buildBatch(merchant, from, to, payments);
            batchRepository.save(batch);
            created++;
            totalTxns += payments.size();
            log.info("Settlement batch created: {} for merchant {} — {} transactions",
                    batch.getSettlementRef(), merchant.getMerchantId(), payments.size());
        }

        return new GenerateResult(created, skipped, totalTxns, from, to);
    }

    private SettlementBatch buildBatch(Merchant merchant, LocalDate from, LocalDate to,
                                       List<Payment> payments) {
        // ── Aggregate totals ─────────────────────────────────────────────────
        BigDecimal totalAmount       = BigDecimal.ZERO;
        BigDecimal totalMsc          = BigDecimal.ZERO;
        BigDecimal totalVat          = BigDecimal.ZERO;
        BigDecimal totalProcFee      = BigDecimal.ZERO;
        BigDecimal totalProcVat      = BigDecimal.ZERO;
        BigDecimal totalAmtPayable   = BigDecimal.ZERO;

        List<SettlementItem> items = new ArrayList<>();

        for (Payment p : payments) {
            totalAmount     = totalAmount.add(p.getAmount());
            totalMsc        = totalMsc.add(p.getMsc());
            totalVat        = totalVat.add(p.getVatAmount());
            totalProcFee    = totalProcFee.add(p.getProcessorFee());
            totalProcVat    = totalProcVat.add(p.getProcessorVat());
            totalAmtPayable = totalAmtPayable.add(p.getAmountPayable());

            items.add(SettlementItem.builder()
                    .payment(p)
                    .amount(p.getAmount())
                    .msc(p.getMsc())
                    .vatAmount(p.getVatAmount())
                    .processorFee(p.getProcessorFee())
                    .processorVat(p.getProcessorVat())
                    .amountPayable(p.getAmountPayable())
                    .build());
        }

        // income = msc - processorFee (MiniPay's take)
        BigDecimal income     = totalMsc.subtract(totalProcFee);
        // payableVat = vatAmount - processorVat
        BigDecimal payableVat = totalVat.subtract(totalProcVat);

        SettlementBatch batch = SettlementBatch.builder()
                .merchant(merchant)
                .periodStart(from)
                .periodEnd(to)
                .count(payments.size())
                .transactionAmount(totalAmount)
                .msc(totalMsc)
                .vatAmount(totalVat)
                .processorFee(totalProcFee)
                .processorVat(totalProcVat)
                .income(income)
                .payableVat(payableVat)
                .amountPayable(totalAmtPayable)
                .status("PENDING")
                .build();

        // Link items to batch
        items.forEach(item -> item.setBatch(batch));
        batch.setItems(items);
        return batch;
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementBatchResponse> listBatches(SettlementFilter filter) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(filter.getSortDir())
                        ? Sort.Direction.DESC : Sort.Direction.ASC,
                filter.getSortBy());
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Page<SettlementBatch> page = batchRepository.findByFilters(
                filter.getMerchantId(), filter.getFrom(), filter.getTo(), pageable);
        return PageResponse.of(page.map(b -> toResponse(b, false)));
    }

    @Transactional(readOnly = true)
    public SettlementBatchResponse getBatch(UUID settlementRef) {
        SettlementBatch batch = batchRepository.findBySettlementRefWithItems(settlementRef)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementBatch", settlementRef.toString()));
        return toResponse(batch, true);
    }

    public SettlementBatchResponse toResponse(SettlementBatch b, boolean includeItems) {
        SettlementBatchResponse r = new SettlementBatchResponse();
        r.setId(b.getId());
        r.setSettlementRef(b.getSettlementRef());
        r.setMerchantId(b.getMerchant().getMerchantId());
        r.setMerchantName(b.getMerchant().getName());
        r.setPeriodStart(b.getPeriodStart());
        r.setPeriodEnd(b.getPeriodEnd());
        r.setCount(b.getCount());
        r.setTransactionAmount(b.getTransactionAmount());
        r.setMsc(b.getMsc());
        r.setVatAmount(b.getVatAmount());
        r.setProcessorFee(b.getProcessorFee());
        r.setProcessorVat(b.getProcessorVat());
        r.setIncome(b.getIncome());
        r.setPayableVat(b.getPayableVat());
        r.setAmountPayable(b.getAmountPayable());
        r.setStatus(b.getStatus());
        r.setCreatedAt(b.getCreatedAt());
        if (includeItems) {
            r.setItems(b.getItems().stream().map(this::toItemResponse).toList());
        }
        return r;
    }

    private SettlementItemResponse toItemResponse(SettlementItem i) {
        SettlementItemResponse r = new SettlementItemResponse();
        r.setId(i.getId());
        r.setPaymentRef(i.getPayment().getPaymentRef());
        r.setOrderId(i.getPayment().getOrderId());
        r.setAmount(i.getAmount());
        r.setMsc(i.getMsc());
        r.setVatAmount(i.getVatAmount());
        r.setProcessorFee(i.getProcessorFee());
        r.setProcessorVat(i.getProcessorVat());
        r.setAmountPayable(i.getAmountPayable());
        return r;
    }
}
