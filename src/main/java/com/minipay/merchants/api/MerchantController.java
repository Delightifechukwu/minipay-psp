package com.minipay.merchants.api;

import com.minipay.common.PageResponse;
import com.minipay.merchants.dto.MerchantDtos.*;
import com.minipay.merchants.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant onboarding and management")
@SecurityRequirement(name = "bearerAuth")
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAKER')")
    @Operation(summary = "Create merchant (MAKER submits for approval, ADMIN creates directly)")
    public ResponseEntity<Object> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        Object result = merchantService.createMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{merchantId}")
    @PreAuthorize("hasAnyRole('ADMIN','MAKER','CHECKER')")
    @Operation(summary = "Update merchant details")
    public ResponseEntity<MerchantResponse> updateMerchant(
            @PathVariable String merchantId,
            @Valid @RequestBody UpdateMerchantRequest request) {
        return ResponseEntity.ok(merchantService.updateMerchant(merchantId, request));
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant by ID")
    public ResponseEntity<MerchantResponse> getMerchant(@PathVariable String merchantId) {
        return ResponseEntity.ok(merchantService.getMerchant(merchantId));
    }

    @GetMapping
    @Operation(summary = "List merchants with filters and pagination")
    public ResponseEntity<PageResponse<MerchantResponse>> listMerchants(
            @ModelAttribute MerchantFilter filter) {
        return ResponseEntity.ok(merchantService.listMerchants(filter));
    }

    @PutMapping("/{merchantId}/charge-settings")
    @PreAuthorize("hasAnyRole('ADMIN','MAKER','CHECKER')")
    @Operation(summary = "Upsert charge settings for a merchant")
    public ResponseEntity<ChargeSettingResponse> upsertChargeSetting(
            @PathVariable String merchantId,
            @Valid @RequestBody ChargeSettingRequest request) {
        return ResponseEntity.ok(merchantService.upsertChargeSetting(merchantId, request));
    }

    @GetMapping("/{merchantId}/charge-settings")
    @Operation(summary = "Get charge settings for a merchant")
    public ResponseEntity<ChargeSettingResponse> getChargeSetting(@PathVariable String merchantId) {
        return ResponseEntity.ok(merchantService.getChargeSetting(merchantId));
    }
}
