package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审核任务消息
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
public record AuditTaskMessage(
        String messageId, // v4.0: 消息唯一标识
        String jobId, // 关联的任务ID
        String postId, // 内容ID (爬虫完成后填充)
        String url, // 内容URL
        LocalDateTime createdAt, // 创建时间
        int retryCount // 重试次数
) {
    /**
     * 便捷构造函数 - 自动生成 messageId 和时间戳
     */
    public AuditTaskMessage(String jobId, String postId, String url) {
        this(
                UUID.randomUUID().toString(),
                jobId,
                postId,
                url,
                LocalDateTime.now(),
                0);
    }

    /**
     * 创建重试消息
     */
    public AuditTaskMessage withRetry() {
        return new AuditTaskMessage(
                UUID.randomUUID().toString(),
                jobId,
                postId,
                url,
                LocalDateTime.now(),
                retryCount + 1);
    }

    /**
     * 是否超过最大重试次数
     */
    public boolean isMaxRetryExceeded(int maxRetries) {
        return retryCount >= maxRetries;
    }
}
