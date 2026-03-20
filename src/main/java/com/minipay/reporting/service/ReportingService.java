package com.minipay.reporting.service;

import com.minipay.common.PageResponse;
import com.minipay.payments.domain.Payment;
import com.minipay.payments.dto.PaymentDtos.PaymentFilter;
import com.minipay.payments.repo.PaymentRepository;
import com.minipay.payments.service.PaymentService;
import com.minipay.reporting.dto.ReportDtos.*;
import com.minipay.settlements.domain.SettlementBatch;
import com.minipay.settlements.dto.SettlementDtos.SettlementBatchResponse;
import com.minipay.settlements.dto.SettlementDtos.SettlementFilter;
import com.minipay.settlements.repo.SettlementBatchRepository;
import com.minipay.settlements.service.SettlementService;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final PaymentRepository paymentRepository;
    private final SettlementBatchRepository batchRepository;
    private final PaymentService paymentService;
    private final SettlementService settlementService;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter D_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── Paginated JSON ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<?> getTransactionReport(TransactionReportFilter f) {
        Sort sort = Sort.by("desc".equalsIgnoreCase(f.getSortDir())
                ? Sort.Direction.DESC : Sort.Direction.ASC, f.getSortBy());
        Pageable pageable = PageRequest.of(f.getPage(), f.getSize(), sort);
        Page<Payment> page = paymentRepository.findByFilters(
                f.getMerchantId(), f.getChannel(), f.getStatus(),
                f.getFrom() != null ? f.getFrom() : Instant.EPOCH,
                f.getTo()   != null ? f.getTo()   : Instant.parse("9999-12-31T23:59:59Z"),
                pageable);
        return PageResponse.of(page.map(paymentService::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<?> getSettlementReport(SettlementReportFilter f) {
        Sort sort = Sort.by("desc".equalsIgnoreCase(f.getSortDir())
                ? Sort.Direction.DESC : Sort.Direction.ASC, f.getSortBy());
        Pageable pageable = PageRequest.of(f.getPage(), f.getSize(), sort);
        Page<SettlementBatch> page = batchRepository.findByFilters(
                f.getMerchantId(), f.getFrom(), f.getTo(), pageable);
        return PageResponse.of(page.map(b -> settlementService.toResponse(b, false)));
    }

    // ── CSV Export ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportTransactionsCsv(TransactionReportFilter f) throws IOException {
        List<Payment> payments = paymentRepository.findForExport(
                f.getMerchantId(), f.getChannel(), f.getStatus(),
                f.getFrom() != null ? f.getFrom() : Instant.EPOCH,
                f.getTo()   != null ? f.getTo()   : Instant.parse("9999-12-31T23:59:59Z"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVWriter csv = new CSVWriter(new OutputStreamWriter(out))) {
            csv.writeNext(txnCsvHeaders());
            for (Payment p : payments) {
                csv.writeNext(toTxnCsvRow(p));
            }
        }
        log.info("Exported {} transactions to CSV", payments.size());
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] exportSettlementsCsv(SettlementReportFilter f) throws IOException {
        List<SettlementBatch> batches = batchRepository
                .findByFilters(f.getMerchantId(), f.getFrom(), f.getTo(), Pageable.unpaged())
                .getContent();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVWriter csv = new CSVWriter(new OutputStreamWriter(out))) {
            csv.writeNext(settlementCsvHeaders());
            for (SettlementBatch b : batches) {
                csv.writeNext(toSettlementCsvRow(b));
            }
        }
        log.info("Exported {} settlement batches to CSV", batches.size());
        return out.toByteArray();
    }

    // ── Excel Export ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportTransactionsExcel(TransactionReportFilter f) throws IOException {
        List<Payment> payments = paymentRepository.findForExport(
                f.getMerchantId(), f.getChannel(), f.getStatus(),
                f.getFrom() != null ? f.getFrom() : Instant.EPOCH,
                f.getTo()   != null ? f.getTo()   : Instant.parse("9999-12-31T23:59:59Z"));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Transactions");
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle moneyStyle  = buildMoneyStyle(wb);
            CellStyle dateStyle   = buildDateStyle(wb);

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("MiniPay Transaction Report");
            titleCell.setCellStyle(buildTitleStyle(wb));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, txnCsvHeaders().length - 1));

            // Header row
            Row headerRow = sheet.createRow(1);
            String[] headers = txnCsvHeaders();
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 2;
            for (Payment p : payments) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(p.getPaymentRef().toString());
                row.createCell(col++).setCellValue(p.getMerchant().getMerchantId());
                row.createCell(col++).setCellValue(p.getOrderId());
                setMoneyCell(row.createCell(col++), p.getAmount(), moneyStyle);
                row.createCell(col++).setCellValue(p.getCurrency());
                row.createCell(col++).setCellValue(p.getChannel());
                row.createCell(col++).setCellValue(p.getStatus());
                setMoneyCell(row.createCell(col++), p.getMsc(), moneyStyle);
                setMoneyCell(row.createCell(col++), p.getVatAmount(), moneyStyle);
                setMoneyCell(row.createCell(col++), p.getProcessorFee(), moneyStyle);
                setMoneyCell(row.createCell(col++), p.getProcessorVat(), moneyStyle);
                setMoneyCell(row.createCell(col++), p.getPayableVat(), moneyStyle);
                setMoneyCell(row.createCell(col++), p.getAmountPayable(), moneyStyle);
                row.createCell(col).setCellValue(DT_FMT.format(p.getCreatedAt()));
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            log.info("Exported {} transactions to Excel", payments.size());
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportSettlementsExcel(SettlementReportFilter f) throws IOException {
        List<SettlementBatch> batches = batchRepository
                .findByFilters(f.getMerchantId(), f.getFrom(), f.getTo(), Pageable.unpaged())
                .getContent();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Settlements");
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle moneyStyle  = buildMoneyStyle(wb);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("MiniPay Settlement Report");
            titleCell.setCellStyle(buildTitleStyle(wb));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, settlementCsvHeaders().length - 1));

            Row headerRow = sheet.createRow(1);
            String[] headers = settlementCsvHeaders();
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 2;
            for (SettlementBatch b : batches) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(b.getSettlementRef().toString());
                row.createCell(col++).setCellValue(b.getMerchant().getMerchantId());
                row.createCell(col++).setCellValue(b.getMerchant().getName());
                row.createCell(col++).setCellValue(D_FMT.format(b.getPeriodStart()));
                row.createCell(col++).setCellValue(D_FMT.format(b.getPeriodEnd()));
                row.createCell(col++).setCellValue(b.getCount());
                setMoneyCell(row.createCell(col++), b.getTransactionAmount(), moneyStyle);
                setMoneyCell(row.createCell(col++), b.getMsc(), moneyStyle);
                setMoneyCell(row.createCell(col++), b.getVatAmount(), moneyStyle);
                setMoneyCell(row.createCell(col++), b.getProcessorFee(), moneyStyle);
                setMoneyCell(row.createCell(col++), b.getProcessorVat(), moneyStyle);
                setMoneyCell(row.createCell(col++), b.getIncome(), moneyStyle);
                setMoneyCell(row.createCell(col++), b.getPayableVat(), moneyStyle);
                setMoneyCell(row.createCell(col++), b.getAmountPayable(), moneyStyle);
                row.createCell(col).setCellValue(b.getStatus());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String[] txnCsvHeaders() {
        return new String[]{
            "PaymentRef","MerchantId","OrderId","Amount","Currency","Channel","Status",
            "MSC","VAT","ProcessorFee","ProcessorVAT","PayableVAT","AmountPayable","CreatedAt"
        };
    }

    private String[] settlementCsvHeaders() {
        return new String[]{
            "SettlementRef","MerchantId","MerchantName","PeriodStart","PeriodEnd",
            "Count","TransactionAmount","MSC","VAT","ProcessorFee","ProcessorVAT",
            "Income","PayableVAT","AmountPayable","Status"
        };
    }

    private String[] toTxnCsvRow(Payment p) {
        return new String[]{
            p.getPaymentRef().toString(),
            p.getMerchant().getMerchantId(),
            p.getOrderId(),
            p.getAmount().toPlainString(),
            p.getCurrency(),
            p.getChannel(),
            p.getStatus(),
            p.getMsc().toPlainString(),
            p.getVatAmount().toPlainString(),
            p.getProcessorFee().toPlainString(),
            p.getProcessorVat().toPlainString(),
            p.getPayableVat().toPlainString(),
            p.getAmountPayable().toPlainString(),
            DT_FMT.format(p.getCreatedAt())
        };
    }

    private String[] toSettlementCsvRow(SettlementBatch b) {
        return new String[]{
            b.getSettlementRef().toString(),
            b.getMerchant().getMerchantId(),
            b.getMerchant().getName(),
            D_FMT.format(b.getPeriodStart()),
            D_FMT.format(b.getPeriodEnd()),
            String.valueOf(b.getCount()),
            b.getTransactionAmount().toPlainString(),
            b.getMsc().toPlainString(),
            b.getVatAmount().toPlainString(),
            b.getProcessorFee().toPlainString(),
            b.getProcessorVat().toPlainString(),
            b.getIncome().toPlainString(),
            b.getPayableVat().toPlainString(),
            b.getAmountPayable().toPlainString(),
            b.getStatus()
        };
    }

    private void setMoneyCell(Cell cell, java.math.BigDecimal val, CellStyle style) {
        if (val != null) cell.setCellValue(val.doubleValue());
        cell.setCellStyle(style);
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle buildTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle buildMoneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle buildDateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }
}
