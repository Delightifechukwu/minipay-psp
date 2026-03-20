package com.minipay.settlements.api;

import com.minipay.common.PageResponse;
import com.minipay.settlements.dto.SettlementDtos.*;
import com.minipay.settlements.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "Settlement batch generation and retrieval")
@SecurityRequirement(name = "bearerAuth")
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger settlement generation for a date range (Admin only)")
    public ResponseEntity<GenerateResult> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(settlementService.generateSettlements(from, to));
    }

    @GetMapping
    @Operation(summary = "List settlement batches with filters")
    public ResponseEntity<PageResponse<SettlementBatchResponse>> listBatches(
            @ModelAttribute SettlementFilter filter) {
        return ResponseEntity.ok(settlementService.listBatches(filter));
    }

    @GetMapping("/{settlementRef}")
    @Operation(summary = "Get settlement batch detail including all line items")
    public ResponseEntity<SettlementBatchResponse> getBatch(@PathVariable UUID settlementRef) {
        return ResponseEntity.ok(settlementService.getBatch(settlementRef));
    }
}
