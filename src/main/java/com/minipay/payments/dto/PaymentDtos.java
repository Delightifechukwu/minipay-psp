package com.minipay.payments.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentDtos {

    @Data
    public static class InitiatePaymentRequest {

        @NotBlank(message = "merchantId is required")
        private String merchantId;

        @NotBlank(message = "orderId is required")
        @Size(max = 100)
        private String orderId;

        @NotNull(message = "amount is required")
        @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
        @Digits(integer = 17, fraction = 2)
        private BigDecimal amount;

        @NotBlank
        @Size(min = 3, max = 3)
        @Pattern(regexp = "NGN|USD|GBP|EUR", message = "Unsupported currency")
        private String currency;

        @NotBlank
        @Pattern(regexp = "CARD|WALLET|BANK_TRANSFER",
                 message = "Channel must be CARD, WALLET, or BANK_TRANSFER")
        private String channel;

        @Size(max = 100)
        private String customerId;

        @Size(max = 500)
        private String callbackUrl;
    }

    @Data
    public static class SimulateCallbackRequest {
        @NotNull
        private UUID paymentRef;

        @NotBlank
        @Pattern(regexp = "SUCCESS|FAILED", message = "Status must be SUCCESS or FAILED")
        private String status;

        @Size(max = 500)
        private String failureReason;
    }

    @Data
    public static class PaymentResponse {
        private Long id;
        private UUID paymentRef;
        private String merchantId;
        private String orderId;
        private BigDecimal amount;
        private String currency;
        private String channel;
        private String status;
        private BigDecimal msc;
        private BigDecimal vatAmount;
        private BigDecimal processorFee;
        private BigDecimal processorVat;
        private BigDecimal payableVat;
        private BigDecimal amountPayable;
        private String customerId;
        private String callbackUrl;
        private String failureReason;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    public static class PaymentFilter {
        private String merchantId;
        private String channel;
        private String status;
        private Instant from;
        private Instant to;
        private int page = 0;
        private int size = 20;
        private String sortBy = "createdAt";
        private String sortDir = "desc";

        public int getSize() { return Math.min(size, 100); }
    }
}
