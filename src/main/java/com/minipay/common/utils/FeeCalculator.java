package com.minipay.common.utils;

import com.minipay.merchants.domain.ChargeSetting;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stateless fee calculator for MiniPay MSC, VAT, and processor fee computations.
 *
 * <p>Business Rules:
 * <ul>
 *   <li>MSC: if useFixedMSC → msc = fixedFee; else msc = min(amount × percentageFee, mscCap) + fixedFee</li>
 *   <li>VAT: vatAmount = msc × vatRate</li>
 *   <li>Processor fee: min(amount × platformProviderRate, platformProviderCap)</li>
 *   <li>Processor VAT: processorFee × vatRate (same rate as merchant VAT)</li>
 *   <li>Payable VAT: vatAmount − processorVat (may be negative — documented)</li>
 *   <li>Amount Payable: amount − (msc + vatAmount + processorFee + processorVat)</li>
 * </ul>
 *
 * <p>All calculations use {@link RoundingMode#HALF_UP} and scale=2 for currency (NGN),
 * except payableVat which retains scale=4 to preserve precision before final rounding.
 */
@Slf4j
public final class FeeCalculator {

    private static final int CURRENCY_SCALE = 2;
    private static final int VAT_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private FeeCalculator() {}

    /**
     * Computes all fee components for a given payment amount and charge settings.
     *
     * @param amount         gross payment amount (must be positive)
     * @param chargeSetting  merchant's charge configuration
     * @return               immutable {@link FeeResult} containing all computed values
     */
    public static FeeResult compute(BigDecimal amount, ChargeSetting chargeSetting) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (chargeSetting == null) {
            throw new IllegalArgumentException("ChargeSetting must not be null");
        }

        // ── 1. MSC (Merchant Service Charge) ────────────────────────────────
        BigDecimal msc;
        if (Boolean.TRUE.equals(chargeSetting.getUseFixedMSC())) {
            msc = nvl(chargeSetting.getFixedFee());
        } else {
            BigDecimal pctFee = amount
                    .multiply(nvl(chargeSetting.getPercentageFee()))
                    .divide(BigDecimal.valueOf(100), VAT_SCALE, ROUNDING);

            // Apply optional MSC cap
            if (chargeSetting.getMscCap() != null &&
                    pctFee.compareTo(chargeSetting.getMscCap()) > 0) {
                pctFee = chargeSetting.getMscCap();
            }
            msc = pctFee.add(nvl(chargeSetting.getFixedFee()));
        }
        msc = msc.setScale(CURRENCY_SCALE, ROUNDING);

        // ── 2. VAT on MSC ────────────────────────────────────────────────────
        BigDecimal vatAmount = msc
                .multiply(nvl(chargeSetting.getVatRate()))
                .divide(BigDecimal.valueOf(100), VAT_SCALE, ROUNDING)
                .setScale(CURRENCY_SCALE, ROUNDING);

        // ── 3. Processor Fee ─────────────────────────────────────────────────
        BigDecimal processorFee = amount
                .multiply(nvl(chargeSetting.getPlatformProviderRate()))
                .divide(BigDecimal.valueOf(100), VAT_SCALE, ROUNDING);

        if (chargeSetting.getPlatformProviderCap() != null &&
                processorFee.compareTo(chargeSetting.getPlatformProviderCap()) > 0) {
            processorFee = chargeSetting.getPlatformProviderCap();
        }
        processorFee = processorFee.setScale(CURRENCY_SCALE, ROUNDING);

        // ── 4. Processor VAT (same vatRate applied to processorFee) ──────────
        BigDecimal processorVat = processorFee
                .multiply(nvl(chargeSetting.getVatRate()))
                .divide(BigDecimal.valueOf(100), VAT_SCALE, ROUNDING)
                .setScale(CURRENCY_SCALE, ROUNDING);

        // ── 5. Payable VAT (MiniPay's net VAT liability — can be negative) ───
        //    payableVat = vatAmount - processorVat
        //    Negative values are valid: it means MiniPay has a VAT credit.
        BigDecimal payableVat = vatAmount.subtract(processorVat)
                .setScale(VAT_SCALE, ROUNDING);

        // ── 6. Amount Payable to Merchant ─────────────────────────────────────
        BigDecimal totalDeductions = msc.add(vatAmount).add(processorFee).add(processorVat);
        BigDecimal amountPayable = amount.subtract(totalDeductions)
                .setScale(CURRENCY_SCALE, ROUNDING);

        FeeResult result = FeeResult.builder()
                .msc(msc)
                .vatAmount(vatAmount)
                .processorFee(processorFee)
                .processorVat(processorVat)
                .payableVat(payableVat)
                .amountPayable(amountPayable)
                .build();

        log.debug("FeeCalc: amount={} → msc={}, vat={}, procFee={}, procVat={}, payableVat={}, amtPayable={}",
                amount, msc, vatAmount, processorFee, processorVat, payableVat, amountPayable);
        return result;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Value
    @Builder
    public static class FeeResult {
        BigDecimal msc;
        BigDecimal vatAmount;
        BigDecimal processorFee;
        BigDecimal processorVat;
        BigDecimal payableVat;   // scale=4; may be negative
        BigDecimal amountPayable;
    }
}
