package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 爬虫任务消息DTO（v4.0）
 * <p>
 * 用于Redis Stream消息队列中传递爬虫任务信息。
 * <p>
 * 设计特点：
 * <ul>
 *   <li>不可变record类型，保证消息传递安全性</li>
 *   <li>messageId用于消息幂等性检查</li>
 *   <li>source字段标识消息来源：API/BATCH/WEBHOOK</li>
 *   <li>支持强制刷新和重试机制</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
public record CrawlTaskMessage(
        /**
         * 消息唯一标识
         * <p>
         * v4.0新增，用于Redis Stream消息去重。
         * Redis键格式：processed:crawl:{messageId}
         */
        String messageId,

        /**
         * 任务ID
         * <p>
         * 对应AuditJob实体的jobId字段，用于关联到具体的审核任务。
         */
        String jobId,

        /**
         * 待爬取的URL
         * <p>
         * 小红书笔记的完整URL地址。
         */
        String url,

        /**
         * 消息来源
         * <p>
         * 标识消息的产生来源，取值范围：
         * <ul>
         *   <li>API - API接口直接提交</li>
         *   <li>BATCH - 批量任务处理</li>
         *   <li>WEBHOOK - Webhook回调触发</li>
         * </ul>
         */
        String source,

        /**
         * 是否强制刷新
         * <p>
         * true-跳过缓存强制重新爬取，false-优先使用缓存
         */
        boolean forceRefresh,

        /**
         * 已重试次数
         * <p>
         * 消息处理失败的次数，每次重试自增1。
         */
        int retryCount,

        /**
         * 消息创建时间
         * <p>
         * 消息生成时的时间戳，用于超时判断。
         */
        LocalDateTime createdAt
) {
    /**
     * 便捷构造函数
     * <p>
     * 自动生成messageId和createdAt，重试次数默认为0。
     *
     * @param jobId         任务ID
     * @param url           待爬取URL
     * @param source        消息来源
     * @param forceRefresh  是否强制刷新
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
     * <p>
     * 生成新的messageId，增加重试次数，更新创建时间。
     *
     * @return 新的CrawlTaskMessage实例
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
     * 检查是否超过最大重试次数
     *
     * @param maxRetries 配置的最大重试次数
     * @return true-已超过，false-还可以重试
     */
    public boolean isMaxRetryExceeded(int maxRetries) {
        return retryCount >= maxRetries;
    }
}
