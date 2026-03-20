package com.minipay.common.errors;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
    public ResourceNotFoundException(String entity, String id) {
        super(entity + " not found with identifier: " + id);
    }
}
