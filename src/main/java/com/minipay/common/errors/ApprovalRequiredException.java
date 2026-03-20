package com.minipay.common.errors;
import lombok.Getter;
@Getter
public class ApprovalRequiredException extends RuntimeException {
    private final String requestRef;
    public ApprovalRequiredException(String requestRef) {
        super("This action requires CHECKER approval. Request submitted for review.");
        this.requestRef = requestRef;
    }
}
