package com.xhs.audit.model.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应格式
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 响应码: "000000"表示成功，其他表示错误
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("000000", "success", data, LocalDateTime.now());
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>("000000", "success", null, LocalDateTime.now());
    }

    /**
     * 失败响应
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }

    /**
     * 失败响应（带数据）
     */
    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, LocalDateTime.now());
    }
}
