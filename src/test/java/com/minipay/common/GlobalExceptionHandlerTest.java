package com.minipay.common;

import com.minipay.common.errors.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private final com.minipay.common.errors.GlobalExceptionHandler handler =
            new com.minipay.common.errors.GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException → 404 with correct title")
    void handleNotFound_returns404() {
        ResponseEntity<ProblemDetail> response =
                handler.handleNotFound(new ResourceNotFoundException("Merchant", "MRC-X"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getDetail()).contains("MRC-X");
    }

    @Test
    @DisplayName("BusinessException → 400 with correct title")
    void handleBusiness_returns400() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBusiness(new BusinessException("Rule violated"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("Business Rule Violation");
        assertThat(response.getBody().getDetail()).isEqualTo("Rule violated");
    }

    @Test
    @DisplayName("ConflictException → 409 with correct title")
    void handleConflict_returns409() {
        ResponseEntity<ProblemDetail> response =
                handler.handleConflict(new ConflictException("Duplicate email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getTitle()).isEqualTo("Conflict");
        assertThat(response.getBody().getDetail()).isEqualTo("Duplicate email");
    }

    @Test
    @DisplayName("IdempotencyConflictException → 409 with payment ref property")
    void handleIdempotency_returns409WithRef() {
        ResponseEntity<ProblemDetail> response =
                handler.handleIdempotency(new IdempotencyConflictException("PAY-123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getTitle()).isEqualTo("Idempotency Key Conflict");
        assertThat(response.getBody().getProperties()).containsKey("existingPaymentRef");
        assertThat(response.getBody().getProperties().get("existingPaymentRef")).isEqualTo("PAY-123");
    }

    @Test
    @DisplayName("ApprovalRequiredException → 202 with requestRef property")
    void handleApprovalRequired_returns202() {
        ResponseEntity<ProblemDetail> response =
                handler.handleApprovalRequired(new ApprovalRequiredException("REQ-001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getTitle()).isEqualTo("Approval Required");
        assertThat(response.getBody().getProperties()).containsKey("requestRef");
    }

    @Test
    @DisplayName("BadCredentialsException → 401 with correct title")
    void handleBadCredentials_returns401() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBadCredentials(new BadCredentialsException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getTitle()).isEqualTo("Authentication Failed");
    }

    @Test
    @DisplayName("AccessDeniedException → 403 with correct title")
    void handleAccessDenied_returns403() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getTitle()).isEqualTo("Access Denied");
    }

    @Test
    @DisplayName("RateLimitExceededException → 429 with retryAfterSeconds property")
    void handleRateLimit_returns429() {
        ResponseEntity<ProblemDetail> response =
                handler.handleRateLimit(new RateLimitExceededException("Too many", 30L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getTitle()).isEqualTo("Rate Limit Exceeded");
        assertThat(response.getBody().getProperties()).containsKey("retryAfterSeconds");
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
    }

    @Test
    @DisplayName("Generic Exception → 500 with Internal Server Error title")
    void handleAll_returns500() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAll(new RuntimeException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
    }
}