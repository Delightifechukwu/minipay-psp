package com.minipay.common;

import com.minipay.common.utils.FeeCalculator;
import com.minipay.common.utils.FeeCalculator.FeeResult;
import com.minipay.merchants.domain.ChargeSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FeeCalculator")
class FeeCalculatorTest {

    // ── Helper ────────────────────────────────────────────────────────────────

    private ChargeSetting setting(double pct, double fixed, boolean useFixed,
                                  Double mscCap, double vatRate,
                                  double providerRate, Double providerCap) {
        ChargeSetting s = new ChargeSetting();
        s.setPercentageFee(bd(pct));
        s.setFixedFee(bd(fixed));
        s.setUseFixedMSC(useFixed);
        s.setMscCap(mscCap != null ? bd(mscCap) : null);
        s.setVatRate(bd(vatRate));
        s.setPlatformProviderRate(bd(providerRate));
        s.setPlatformProviderCap(providerCap != null ? bd(providerCap) : null);
        return s;
    }

    private BigDecimal bd(double v) { return BigDecimal.valueOf(v); }

    // ── Spec Example 1 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Spec Example 1: amount=100,000 pct=1.5% fixed=50 cap=2,000 vat=7.5% provRate=1% provCap=1,200")
    class SpecExample1 {

        final ChargeSetting cs = setting(1.5, 50, false, 2000.0, 7.5, 1.0, 1200.0);
        final FeeResult result = FeeCalculator.compute(bd(100_000), cs);

        @Test void msc_is_1550() {
            // pct = 100,000 × 1.5% = 1,500 < cap 2,000 → stays 1,500; + fixed 50 = 1,550
            assertThat(result.getMsc()).isEqualByComparingTo("1550.00");
        }

        @Test void vatAmount_is_116_25() {
            // 1,550 × 7.5% = 116.25
            assertThat(result.getVatAmount()).isEqualByComparingTo("116.25");
        }

        @Test void processorFee_is_1000() {
            // 100,000 × 1% = 1,000 < cap 1,200
            assertThat(result.getProcessorFee()).isEqualByComparingTo("1000.00");
        }

        @Test void processorVat_is_75() {
            // 1,000 × 7.5% = 75
            assertThat(result.getProcessorVat()).isEqualByComparingTo("75.00");
        }

        @Test void payableVat_is_41_25() {
            // 116.25 - 75 = 41.25
            assertThat(result.getPayableVat()).isEqualByComparingTo("41.25");
        }

        @Test void amountPayable_is_97258_75() {
            // 100,000 - (1,550 + 116.25 + 1,000 + 75) = 97,258.75
            assertThat(result.getAmountPayable()).isEqualByComparingTo("97258.75");
        }
    }

    // ── Spec Example 2 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Spec Example 2: amount=50,000 pct=3% cap=1,000 fixed=0 vat=7.5% provRate=0.8% provCap=500")
    class SpecExample2 {

        final ChargeSetting cs = setting(3.0, 0, false, 1000.0, 7.5, 0.8, 500.0);
        final FeeResult result = FeeCalculator.compute(bd(50_000), cs);

        @Test void msc_is_1000_capped() {
            // pct = 50,000 × 3% = 1,500 > cap 1,000 → capped at 1,000; no fixed → 1,000
            assertThat(result.getMsc()).isEqualByComparingTo("1000.00");
        }

        @Test void vatAmount_is_75() {
            assertThat(result.getVatAmount()).isEqualByComparingTo("75.00");
        }

        @Test void processorFee_is_400() {
            // 50,000 × 0.8% = 400 < cap 500
            assertThat(result.getProcessorFee()).isEqualByComparingTo("400.00");
        }

        @Test void processorVat_is_30() {
            assertThat(result.getProcessorVat()).isEqualByComparingTo("30.00");
        }

        @Test void payableVat_is_45() {
            assertThat(result.getPayableVat()).isEqualByComparingTo("45.00");
        }

        @Test void amountPayable_is_48495() {
            assertThat(result.getAmountPayable()).isEqualByComparingTo("48495.00");
        }
    }

    // ── Edge Cases ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("useFixedMSC = true ignores percentage and cap")
    class FixedMscMode {

        @Test void uses_only_fixed_fee() {
            ChargeSetting cs = setting(5.0, 200, true, 100.0, 7.5, 1.0, null);
            FeeResult r = FeeCalculator.compute(bd(10_000), cs);
            // should use fixedFee=200 regardless of pct=5% or cap=100
            assertThat(r.getMsc()).isEqualByComparingTo("200.00");
            assertThat(r.getVatAmount()).isEqualByComparingTo("15.00"); // 200 × 7.5%
        }
    }

    @Nested
    @DisplayName("Provider cap kicks in when fee exceeds cap")
    class ProviderCap {

        @Test void provider_fee_capped() {
            ChargeSetting cs = setting(1.5, 0, false, null, 7.5, 2.0, 100.0);
            // provider: 10,000 × 2% = 200 > cap 100 → 100
            FeeResult r = FeeCalculator.compute(bd(10_000), cs);
            assertThat(r.getProcessorFee()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("Negative payableVat is handled correctly")
    class NegativePayableVat {

        @Test void payable_vat_can_be_negative() {
            // vatRate=5%, provider has higher effective cost via cap
            // Force: very small MSC, large processorVat
            ChargeSetting cs = setting(0.1, 0, false, null, 5.0, 3.0, null);
            // amount=1,000; msc=1; vatAmount=0.05; processorFee=30; processorVat=1.50
            // payableVat = 0.05 - 1.50 = -1.45 (negative — documented acceptable)
            FeeResult r = FeeCalculator.compute(bd(1_000), cs);
            assertThat(r.getPayableVat()).isLessThan(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Zero rates produce zero fees")
    class ZeroRates {

        @Test void all_zero_rates() {
            ChargeSetting cs = setting(0, 0, false, null, 0, 0, null);
            FeeResult r = FeeCalculator.compute(bd(50_000), cs);
            assertThat(r.getMsc()).isEqualByComparingTo("0.00");
            assertThat(r.getVatAmount()).isEqualByComparingTo("0.00");
            assertThat(r.getProcessorFee()).isEqualByComparingTo("0.00");
            assertThat(r.getAmountPayable()).isEqualByComparingTo("50000.00");
        }
    }

    @Nested
    @DisplayName("Input validation")
    class Validation {

        @Test void null_amount_throws() {
            ChargeSetting cs = setting(1.5, 0, false, null, 7.5, 1.0, null);
            assertThatThrownBy(() -> FeeCalculator.compute(null, cs))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test void zero_amount_throws() {
            ChargeSetting cs = setting(1.5, 0, false, null, 7.5, 1.0, null);
            assertThatThrownBy(() -> FeeCalculator.compute(BigDecimal.ZERO, cs))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test void null_charge_setting_throws() {
            assertThatThrownBy(() -> FeeCalculator.compute(bd(100), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
