package com.xhs.audit.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrawlTaskMessage 测试
 * v4.0: 验证爬虫任务消息 DTO
 *
 * @author XHS Audit System
 * @since 2026-02-03
 */
@DisplayName("CrawlTaskMessage - 爬虫任务消息测试")
class CrawlTaskMessageTest {

    @Test
    @DisplayName("便捷构造函数 - 自动生成 messageId 和时间戳")
    void testConvenienceConstructor() {
        // Act
        CrawlTaskMessage message = new CrawlTaskMessage("job-001", "https://example.com", "API", false);

        // Assert
        assertNotNull(message.messageId());
        assertEquals("job-001", message.jobId());
        assertEquals("https://example.com", message.url());
        assertEquals("API", message.source());
        assertFalse(message.forceRefresh());
        assertEquals(0, message.retryCount());
        assertNotNull(message.createdAt());
    }

    @Test
    @DisplayName("便捷构造函数 - forceRefresh 为 true")
    void testConvenienceConstructorWithForceRefresh() {
        // Act
        CrawlTaskMessage message = new CrawlTaskMessage("job-002", "https://example.com", "BATCH", true);

        // Assert
        assertTrue(message.forceRefresh());
        assertEquals("BATCH", message.source());
    }

    @Test
    @DisplayName("withRetry - 创建重试消息")
    void testWithRetry() {
        // Arrange
        CrawlTaskMessage original = new CrawlTaskMessage("job-001", "https://example.com", "API", false);

        // Act
        CrawlTaskMessage retry = original.withRetry();

        // Assert
        assertNotNull(retry.messageId());
        assertEquals("job-001", retry.jobId());
        assertEquals("https://example.com", retry.url());
        assertEquals(1, retry.retryCount());
        assertTrue(retry.createdAt().isAfter(original.createdAt()) || retry.createdAt().equals(original.createdAt()));
    }

    @Test
    @DisplayName("withRetry - 多次重试")
    void testMultipleRetries() {
        // Arrange
        CrawlTaskMessage original = new CrawlTaskMessage("job-001", "https://example.com", "API", false);

        // Act
        CrawlTaskMessage retry1 = original.withRetry();
        CrawlTaskMessage retry2 = retry1.withRetry();
        CrawlTaskMessage retry3 = retry2.withRetry();

        // Assert
        assertEquals(0, original.retryCount());
        assertEquals(1, retry1.retryCount());
        assertEquals(2, retry2.retryCount());
        assertEquals(3, retry3.retryCount());
    }

    @Test
    @DisplayName("isMaxRetryExceeded - 未超过最大重试次数")
    void testIsMaxRetryExceededFalse() {
        // Arrange
        CrawlTaskMessage message = new CrawlTaskMessage("job-001", "https://example.com", "API", false);

        // Assert - 0 retries < 3 maxRetries
        assertFalse(message.isMaxRetryExceeded(3));
        // 0 retries >= 0 maxRetries is true
        assertTrue(message.isMaxRetryExceeded(0));
    }

    @Test
    @DisplayName("isMaxRetryExceeded - 超过最大重试次数")
    void testIsMaxRetryExceededTrue() {
        // Arrange
        CrawlTaskMessage message = new CrawlTaskMessage("job-001", "https://example.com", "API", false);
        CrawlTaskMessage retryMessage = message.withRetry().withRetry().withRetry();

        // Assert
        assertTrue(retryMessage.isMaxRetryExceeded(2));
        assertTrue(retryMessage.isMaxRetryExceeded(3));
    }

    @Test
    @DisplayName("全参数构造函数")
    void testFullConstructor() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Act
        CrawlTaskMessage message = new CrawlTaskMessage(
                "msg-001",
                "job-001",
                "https://example.com",
                "WEBHOOK",
                true,
                2,
                now
        );

        // Assert
        assertEquals("msg-001", message.messageId());
        assertEquals("job-001", message.jobId());
        assertEquals("https://example.com", message.url());
        assertEquals("WEBHOOK", message.source());
        assertTrue(message.forceRefresh());
        assertEquals(2, message.retryCount());
        assertEquals(now, message.createdAt());
    }

    @Test
    @DisplayName("record 属性 - 所有字段可访问")
    void testRecordProperties() {
        // Arrange
        CrawlTaskMessage message = new CrawlTaskMessage("job-001", "https://example.com", "API", false);

        // Assert - 验证所有属性
        assertEquals("job-001", message.jobId());
        assertEquals("https://example.com", message.url());
        assertEquals("API", message.source());
        assertFalse(message.forceRefresh());
        assertEquals(0, message.retryCount());
        assertNotNull(message.createdAt());
    }
}
