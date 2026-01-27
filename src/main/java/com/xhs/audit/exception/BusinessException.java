package com.xhs.audit.exception;

import lombok.Getter;

/**
 * 业务异常
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
