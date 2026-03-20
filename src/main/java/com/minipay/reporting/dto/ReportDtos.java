package com.minipay.reporting.dto;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

public class ReportDtos {

    @Data
    public static class TransactionReportFilter {
        private String merchantId;
        private String channel;
        private String status;
        private Instant from;
        private Instant to;
        private int page = 0;
        private int size = 20;
        private String sortBy = "createdAt";
        private String sortDir = "desc";
        private String format; // null = JSON, CSV, XLSX

        public int getSize() { return Math.min(size, 100); }
    }

    @Data
    public static class SettlementReportFilter {
        private String merchantId;
        private LocalDate from;
        private LocalDate to;
        private int page = 0;
        private int size = 20;
        private String sortBy = "createdAt";
        private String sortDir = "desc";
        private String format; // null = JSON, CSV, XLSX

        public int getSize() { return Math.min(size, 100); }
    }
}
