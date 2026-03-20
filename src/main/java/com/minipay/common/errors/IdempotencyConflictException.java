package com.minipay.common.errors;
import lombok.Getter;
@Getter
public class IdempotencyConflictException extends RuntimeException {
    private final String existingRef;
    public IdempotencyConflictException(String existingRef) {
        super("A payment with this idempotency key already exists");
        this.existingRef = existingRef;
    }
}
