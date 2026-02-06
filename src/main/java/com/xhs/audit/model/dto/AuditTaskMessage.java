package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审核任务消息DTO（v4.0）
 * <p>
 * 用于Redis Stream消息队列中传递审核任务信息。
 * <p>
 * 设计特点：
 * <ul>
 *   <li>不可变record类型，保证消息传递安全性</li>
 *   <li>自动生成messageId，实现消息幂等性</li>
 *   <li>支持重试机制，记录重试次数</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
public record AuditTaskMessage(
        /**
         * 消息唯一标识
         * <p>
         * v4.0新增，用于Redis Stream消息去重和幂等性检查。
         * 自动生成UUID，确保每条消息在全球范围内唯一。
         */
        String messageId,

        /**
         * 关联的任务ID
         * <p>
         * 对应AuditJob实体的jobId字段，用于关联到具体的审核任务。
         */
        String jobId,

        /**
         * 小红书内容ID
         * <p>
         * 爬虫Worker完成爬取后填充，用于查询xhs_content表。
         * 爬虫Worker处理前为null。
         */
        String postId,

        /**
         * 待审核的内容URL
         * <p>
         * 小红书笔记的完整URL地址。
         */
        String url,

        /**
         * 消息创建时间
         * <p>
         * 消息生成时的时间戳，用于消息生命周期管理和超时判断。
         */
        LocalDateTime createdAt,

        /**
         * 已重试次数
         * <p>
         * 消息处理失败的重试次数，每次重试自增1。
         * 用于控制最大重试次数，避免无限循环。
         */
        int retryCount
) {
    /**
     * 便捷构造函数
     * <p>
     * 自动生成messageId和createdAt，简化消息创建。
     *
     * @param jobId  任务ID
     * @param postId 内容ID（爬虫完成后填充）
     * @param url    内容URL
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
     * <p>
     * 复制当前消息，生成新的messageId，重置时间戳，增加重试次数。
     *
     * @return 新的AuditTaskMessage实例
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
     * 检查是否超过最大重试次数
     *
     * @param maxRetries 配置的最大重试次数
     * @return true-已超过最大重试次数，false-还可以重试
     */
    public boolean isMaxRetryExceeded(int maxRetries) {
        return retryCount >= maxRetries;
    }
}
