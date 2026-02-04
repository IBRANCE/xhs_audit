package com.xhs.audit.config;

/**
 * Redis Stream 配置常量
 * v4.0: 集中管理 Stream 名称和消费者组名称
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
public final class RedisStreamConstants {

    private RedisStreamConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Stream 名称
    public static final String CRAWL_STREAM = "xhs:stream:crawl";
    public static final String AUDIT_STREAM = "xhs:stream:audit";
    public static final String DEAD_LETTER_STREAM = "xhs:stream:dead-letter";

    // 消费者组名称
    public static final String CRAWL_GROUP = "crawl-workers";
    public static final String AUDIT_GROUP = "audit-workers";

    // 去重 Key 前缀
    public static final String DEDUPE_PREFIX_CRAWL = "processed:crawl:";

    // 分布式锁前缀
    public static final String LOCK_PREFIX_CRAWLER = "crawler:processing:";
    public static final String LOCK_PREFIX_AUDIT = "audit:processing:";
}
