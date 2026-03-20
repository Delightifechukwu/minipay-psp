package com.minipay.settlements.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class SettlementDtos {

    @Data
    public static class SettlementBatchResponse {
        private Long id;
        private UUID settlementRef;
        private String merchantId;
        private String merchantName;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private int count;
        private BigDecimal transactionAmount;
        private BigDecimal msc;
        private BigDecimal vatAmount;
        private BigDecimal processorFee;
        private BigDecimal processorVat;
        private BigDecimal income;
        private BigDecimal payableVat;
        private BigDecimal amountPayable;
        private String status;
        private Instant createdAt;
        private List<SettlementItemResponse> items;
    }

    @Data
    public static class SettlementItemResponse {
        private Long id;
        private UUID paymentRef;
        private String orderId;
        private BigDecimal amount;
        private BigDecimal msc;
        private BigDecimal vatAmount;
        private BigDecimal processorFee;
        private BigDecimal processorVat;
        private BigDecimal amountPayable;
    }

    @Data
    public static class SettlementFilter {
        private String merchantId;
        private LocalDate from;
        private LocalDate to;
        private int page = 0;
        private int size = 20;
        private String sortBy = "createdAt";
        private String sortDir = "desc";
    }

    @Data
    public static class GenerateResult {
        private int batchesCreated;
        private int batchesSkipped;
        private int totalTransactions;
        private LocalDate periodStart;
        private LocalDate periodEnd;

        public GenerateResult(int created, int skipped, int txns, LocalDate from, LocalDate to) {
            this.batchesCreated    = created;
            this.batchesSkipped    = skipped;
            this.totalTransactions = txns;
            this.periodStart       = from;
            this.periodEnd         = to;
        }
    }
}
