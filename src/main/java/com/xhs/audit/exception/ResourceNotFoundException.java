package com.xhs.audit.exception;

import lombok.Getter;

/**
 * 资源未找到异常
 * <p>
 * 用于表示请求的资源不存在，如查询不存在的任务ID、帖子ID等。
 * <p>
 * HTTP映射：返回 404 Not Found
 * <p>
 * 使用示例：
 * <pre>
 * throw new ResourceNotFoundException("ERR_JOB_NOT_FOUND", "任务不存在: job-001");
 * throw new ResourceNotFoundException("ERR_POST_NOT_FOUND", "帖子不存在: post-123");
 * </pre>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    /**
     * 错误码
     */
    private final String code;

    /**
     * 创建资源未找到异常
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
}
