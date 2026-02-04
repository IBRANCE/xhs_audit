package com.xhs.audit.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditTaskMessage 测试
 * v4.0: 验证审核任务消息 DTO
 *
 * @author XHS Audit System
 * @since 2026-02-03
 */
@DisplayName("AuditTaskMessage - 审核任务消息测试")
class AuditTaskMessageTest {

    @Test
    @DisplayName("便捷构造函数 - 自动生成 messageId 和时间戳")
    void testConvenienceConstructor() {
        // Act
        AuditTaskMessage message = new AuditTaskMessage("job-001", "post-001", "https://example.com/post/001");

        // Assert
        assertNotNull(message.messageId());
        assertEquals("job-001", message.jobId());
        assertEquals("post-001", message.postId());
        assertEquals("https://example.com/post/001", message.url());
        assertEquals(0, message.retryCount());
        assertNotNull(message.createdAt());
    }

    @Test
    @DisplayName("withRetry - 创建重试消息")
    void testWithRetry() {
        // Arrange
        AuditTaskMessage original = new AuditTaskMessage("job-001", "post-001", "https://example.com");

        // Act
        AuditTaskMessage retry = original.withRetry();

        // Assert
        assertNotNull(retry.messageId());
        assertEquals("job-001", retry.jobId());
        assertEquals("post-001", retry.postId());
        assertEquals(1, retry.retryCount());
    }

    @Test
    @DisplayName("withRetry - 保留原始信息")
    void testWithRetryPreservesOriginalInfo() {
        // Arrange
        AuditTaskMessage original = new AuditTaskMessage("job-001", "post-001", "https://example.com");

        // Act
        AuditTaskMessage retry = original.withRetry();

        // Assert
        assertNotEquals(original.messageId(), retry.messageId());
        assertEquals(original.jobId(), retry.jobId());
        assertEquals(original.postId(), retry.postId());
        assertEquals(original.url(), retry.url());
    }

    @Test
    @DisplayName("isMaxRetryExceeded - 边界条件")
    void testIsMaxRetryExceeded() {
        // Arrange
        AuditTaskMessage message0Retries = new AuditTaskMessage("job-001", "post-001", "url");
        AuditTaskMessage message3Retries = message0Retries.withRetry().withRetry().withRetry();

        // Assert
        assertFalse(message0Retries.isMaxRetryExceeded(3));
        assertTrue(message3Retries.isMaxRetryExceeded(3));
        assertFalse(message3Retries.isMaxRetryExceeded(5));
    }

    @Test
    @DisplayName("全参数构造函数")
    void testFullConstructor() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Act
        AuditTaskMessage message = new AuditTaskMessage(
                "msg-001",
                "job-001",
                "post-001",
                "https://example.com/post/001",
                now,
                2
        );

        // Assert
        assertEquals("msg-001", message.messageId());
        assertEquals("job-001", message.jobId());
        assertEquals("post-001", message.postId());
        assertEquals("https://example.com/post/001", message.url());
        assertEquals(now, message.createdAt());
        assertEquals(2, message.retryCount());
    }

    @Test
    @DisplayName("record equals 和 hashCode")
    void testEqualsAndHashCode() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        AuditTaskMessage message1 = new AuditTaskMessage("msg-001", "job-001", "post-001", "url", now, 0);
        AuditTaskMessage message2 = new AuditTaskMessage("msg-001", "job-001", "post-001", "url", now, 0);

        // Assert
        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
    }

    @Test
    @DisplayName("不同 messageId 的消息不相等")
    void testDifferentMessageIdNotEqual() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        AuditTaskMessage message1 = new AuditTaskMessage("msg-001", "job-001", "post-001", "url", now, 0);
        AuditTaskMessage message2 = new AuditTaskMessage("msg-002", "job-001", "post-001", "url", now, 0);

        // Assert
        assertNotEquals(message1, message2);
    }
}
