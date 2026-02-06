package com.xhs.audit.model.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应格式
 * <p>
 * 所有REST API接口统一使用此格式返回响应。
 * <p>
 * 响应码规范：
 * <ul>
 *   <li>000000 - 请求成功</li>
 *   <li>ERR_XXX - 错误响应</li>
 * </ul>
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
     * 响应码
     * <p>
     * "000000"表示成功，其他表示错误。
     * 错误码由GlobalExceptionHandler统一处理。
     */
    private String code;

    /**
     * 响应消息
     * <p>
     * 成功时为"success"，错误时为具体的错误描述信息。
     */
    private String message;

    /**
     * 响应数据
     * <p>
     * 请求成功时返回的业务数据，类型由泛型T指定。
     * 错误响应时为null。
     */
    private T data;

    /**
     * 时间戳
     * <p>
     * 响应生成的时间戳，格式为yyyy-MM-dd'T'HH:mm:ss
     */
    private LocalDateTime timestamp;

    /**
     * 创建成功响应
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 标准成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("000000", "success", data, LocalDateTime.now());
    }

    /**
     * 创建成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return 标准成功响应（无data字段）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>("000000", "success", null, LocalDateTime.now());
    }

    /**
     * 创建错误响应
     *
     * @param code    错误码
     * @param message 错误描述
     * @param <T>    数据类型
     * @return 标准错误响应
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }

    /**
     * 创建错误响应（带错误详情）
     *
     * @param code    错误码
     * @param message 错误描述
     * @param data    错误详情数据
     * @param <T>     数据类型
     * @return 带详细数据的错误响应
     */
    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, LocalDateTime.now());
    }
}
