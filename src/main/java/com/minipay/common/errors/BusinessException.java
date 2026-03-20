package com.minipay.common.errors;
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
