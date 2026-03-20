package com.minipay.payments.api;

import com.minipay.common.PageResponse;
import com.minipay.payments.dto.PaymentDtos.*;
import com.minipay.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment initiation and status management")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/v1/payments")
    @Operation(
        summary = "Initiate a payment",
        description = "Supply `Idempotency-Key` header for safe retries. " +
                      "Returns existing payment if key already used."
    )
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @Parameter(description = "Optional idempotency key for safe retries")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentResponse response = paymentService.initiatePayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/payments/{paymentRef}")
    @Operation(summary = "Get payment by reference UUID")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentRef) {
        return ResponseEntity.ok(paymentService.getPayment(paymentRef));
    }

    @GetMapping("/api/v1/payments")
    @Operation(summary = "List payments with filters and pagination")
    public ResponseEntity<PageResponse<PaymentResponse>> listPayments(
            @ModelAttribute PaymentFilter filter) {
        return ResponseEntity.ok(paymentService.listPayments(filter));
    }

    @PostMapping("/api/v1/simulate/processor-callback")
    @PreAuthorize("hasAnyRole('ADMIN','MAKER')")
    @Operation(
        summary = "Simulate processor callback (test/dev only)",
        description = "Transitions a PENDING payment to SUCCESS or FAILED"
    )
    public ResponseEntity<PaymentResponse> simulateCallback(
            @Valid @RequestBody SimulateCallbackRequest request) {
        return ResponseEntity.ok(paymentService.simulateProcessorCallback(request));
    }
}
