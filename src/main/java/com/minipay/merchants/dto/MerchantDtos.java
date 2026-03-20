package com.minipay.merchants.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

public class MerchantDtos {

    @Data
    public static class CreateMerchantRequest {
        @NotBlank @Size(max = 200)
        private String name;

        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 10, max = 20)
        @Pattern(regexp = "^[0-9]+$", message = "Settlement account must be numeric")
        private String settlementAccount;

        @NotBlank @Size(max = 100)
        private String settlementBank;

        @Size(max = 500)
        private String callbackUrl;
    }

    @Data
    public static class UpdateMerchantRequest {
        @Size(max = 200)
        private String name;

        @Email
        private String email;

        @Size(min = 10, max = 20)
        @Pattern(regexp = "^[0-9]+$", message = "Settlement account must be numeric")
        private String settlementAccount;

        @Size(max = 100)
        private String settlementBank;

        @Size(max = 500)
        private String callbackUrl;

        @Pattern(regexp = "ACTIVE|SUSPENDED", message = "Status must be ACTIVE or SUSPENDED")
        private String status;
    }

    @Data
    public static class ChargeSettingRequest {
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
        private BigDecimal percentageFee;

        @NotNull @DecimalMin("0.0")
        private BigDecimal fixedFee;

        @NotNull
        private Boolean useFixedMsc;

        @DecimalMin("0.0")
        private BigDecimal mscCap;

        @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
        private BigDecimal vatRate;

        @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
        private BigDecimal platformProviderRate;

        @DecimalMin("0.0")
        private BigDecimal platformProviderCap;
    }

    @Data
    public static class MerchantResponse {
        private Long id;
        private String merchantId;
        private String name;
        private String email;
        private String status;
        private String settlementAccount;
        private String settlementBank;
        private String callbackUrl;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    public static class ChargeSettingResponse {
        private Long id;
        private BigDecimal percentageFee;
        private BigDecimal fixedFee;
        private Boolean useFixedMsc;
        private BigDecimal mscCap;
        private BigDecimal vatRate;
        private BigDecimal platformProviderRate;
        private BigDecimal platformProviderCap;
        private Instant updatedAt;
    }

    @Data
    public static class MerchantFilter {
        private String status;
        private String name;
        private int page = 0;
        private int size = 20;
        private String sortBy = "createdAt";
        private String sortDir = "desc";

        public int getSize() { return Math.min(size, 100); }
    }
}
