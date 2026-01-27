package com.xhs.audit.exception;

import lombok.Getter;

/**
 * 资源未找到异常
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final String code;

    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
}
