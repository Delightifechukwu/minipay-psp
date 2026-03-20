package com.minipay.merchants.api;

import com.minipay.common.audit.ApprovalRequestRepository;
import com.minipay.common.errors.ResourceNotFoundException;
import com.minipay.merchants.dto.MerchantDtos.MerchantResponse;
import com.minipay.merchants.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "MAKER/CHECKER approval workflow")
@SecurityRequirement(name = "bearerAuth")
public class ApprovalController {

    private final MerchantService merchantService;
    private final ApprovalRequestRepository approvalRequestRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER')")
    @Operation(summary = "List pending approval requests")
    public ResponseEntity<?> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(approvalRequestRepository.findByStatus("PENDING", pageable));
    }

    @PostMapping("/{requestRef}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER')")
    @Operation(summary = "Approve a pending request")
    public ResponseEntity<MerchantResponse> approve(
            @PathVariable UUID requestRef,
            @RequestBody(required = false) ApprovalDecision decision) {
        String note = decision != null ? decision.getNote() : null;
        return ResponseEntity.ok(merchantService.approveRequest(requestRef, note));
    }

    @PostMapping("/{requestRef}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER')")
    @Operation(summary = "Reject a pending request")
    public ResponseEntity<Void> reject(
            @PathVariable UUID requestRef,
            @RequestBody(required = false) ApprovalDecision decision) {
        String note = decision != null ? decision.getNote() : "No reason provided";
        merchantService.rejectRequest(requestRef, note);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{requestRef}")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','MAKER')")
    @Operation(summary = "Get approval request details")
    public ResponseEntity<?> getRequest(@PathVariable UUID requestRef) {
        return ResponseEntity.ok(
                approvalRequestRepository.findByRequestRef(requestRef)
                        .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", requestRef.toString()))
        );
    }

    @Data
    public static class ApprovalDecision {
        private String note;
    }
}
