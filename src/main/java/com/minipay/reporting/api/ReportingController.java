package com.minipay.reporting.api;

import com.minipay.reporting.dto.ReportDtos.*;
import com.minipay.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Transaction and settlement reports with CSV/XLSX export")
@SecurityRequirement(name = "bearerAuth")
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/transactions")
    @Operation(summary = "Transaction report — JSON (paginated) or export (CSV/XLSX). " +
                         "Add ?format=CSV or ?format=XLSX for file download.")
    public ResponseEntity<?> transactions(@ModelAttribute TransactionReportFilter filter)
            throws Exception {

        if ("CSV".equalsIgnoreCase(filter.getFormat())) {
            byte[] data = reportingService.exportTransactionsCsv(filter);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"transactions.csv\"")
                    .body(data);
        }

        if ("XLSX".equalsIgnoreCase(filter.getFormat())) {
            byte[] data = reportingService.exportTransactionsExcel(filter);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"transactions.xlsx\"")
                    .body(data);
        }

        return ResponseEntity.ok(reportingService.getTransactionReport(filter));
    }

    @GetMapping("/settlements")
    @Operation(summary = "Settlement report — JSON (paginated) or export (CSV/XLSX). " +
                         "Add ?format=CSV or ?format=XLSX for file download.")
    public ResponseEntity<?> settlements(@ModelAttribute SettlementReportFilter filter)
            throws Exception {

        if ("CSV".equalsIgnoreCase(filter.getFormat())) {
            byte[] data = reportingService.exportSettlementsCsv(filter);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"settlements.csv\"")
                    .body(data);
        }

        if ("XLSX".equalsIgnoreCase(filter.getFormat())) {
            byte[] data = reportingService.exportSettlementsExcel(filter);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"settlements.xlsx\"")
                    .body(data);
        }

        return ResponseEntity.ok(reportingService.getSettlementReport(filter));
    }
}
