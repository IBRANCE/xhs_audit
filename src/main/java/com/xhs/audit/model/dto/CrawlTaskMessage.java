package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 爬虫任务消息
 * v4.0: 添加 messageId 用于幂等性检查
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
public record CrawlTaskMessage(
        String messageId, // v4.0: 消息唯一标识 (用于去重)
        String jobId, // 任务ID (对应 AuditJob.jobId)
        String url, // 待爬取的URL
        String source, // 来源: API/BATCH/WEBHOOK
        boolean forceRefresh, // 是否强制刷新
        int retryCount, // v4.0: 重试次数
        LocalDateTime createdAt // 创建时间
) {
    /**
     * 便捷构造函数 - 自动生成 messageId 和时间戳
     */
    public CrawlTaskMessage(String jobId, String url, String source, boolean forceRefresh) {
        this(
                UUID.randomUUID().toString(),
                jobId,
                url,
                source,
                forceRefresh,
                0,
                LocalDateTime.now());
    }

    /**
     * 创建重试消息
     */
    public CrawlTaskMessage withRetry() {
        return new CrawlTaskMessage(
                UUID.randomUUID().toString(),
                jobId,
                url,
                source,
                forceRefresh,
                retryCount + 1,
                LocalDateTime.now());
    }

    /**
     * 是否超过最大重试次数
     */
    public boolean isMaxRetryExceeded(int maxRetries) {
        return retryCount >= maxRetries;
    }
}
