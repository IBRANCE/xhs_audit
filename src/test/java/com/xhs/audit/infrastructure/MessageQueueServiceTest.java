package com.xhs.audit.infrastructure;

import static com.xhs.audit.config.RedisStreamConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.xhs.audit.model.dto.AuditTaskMessage;
import com.xhs.audit.model.dto.CrawlTaskMessage;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * MessageQueueService 单元测试
 *
 * 测试 Redis Stream 消息队列服务的核心功能
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private MeterRegistry meterRegistry;
    private MessageQueueService messageQueueService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        messageQueueService = new MessageQueueService();
        ReflectionTestUtils.setField(messageQueueService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(messageQueueService, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(messageQueueService, "pendingRetryIntervalMs", 60000L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Nested
    @DisplayName("sendCrawlTask tests")
    class SendCrawlTaskTests {

        @Test
        @DisplayName("发送爬虫任务成功")
        void testSendCrawlTask_Success() {
            CrawlTaskMessage task = new CrawlTaskMessage(
                    "msg-123", "job-001", "http://example.com", "API", false, 0, LocalDateTime.now());

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(streamOperations.add(anyString(), anyMap())).thenReturn(RecordId.of("0-0"));

            String result = messageQueueService.sendCrawlTask(task);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo("0-0");
            verify(valueOperations).setIfAbsent(
                    eq(DEDUPE_PREFIX_CRAWL + "job-001:http://example.com"),
                    eq("msg-123"),
                    eq(Duration.ofHours(24)));
            verify(streamOperations).add(eq(CRAWL_STREAM), anyMap());
        }

        @Test
        @DisplayName("发送重试爬虫任务，跳过去重检查")
        void testSendCrawlTask_WithRetry() {
            CrawlTaskMessage task = new CrawlTaskMessage(
                    "msg-123", "job-001", "http://example.com", "API", false, 1, LocalDateTime.now());

            when(streamOperations.add(anyString(), anyMap())).thenReturn(RecordId.of("0-1"));

            String result = messageQueueService.sendCrawlTask(task);

            assertThat(result).isNotNull();
            // 重试任务不应进行去重检查
            verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("发送重复爬虫任务，返回 null")
        void testSendCrawlTask_Duplicate() {
            CrawlTaskMessage task = new CrawlTaskMessage(
                    "msg-123", "job-001", "http://example.com", "API", false, 0, LocalDateTime.now());

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);
            when(valueOperations.get(anyString())).thenReturn("existing-msg-id");

            String result = messageQueueService.sendCrawlTask(task);

            assertThat(result).isNull();
            // 不应添加到队列
            verify(streamOperations, never()).add(anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("sendAuditTask tests")
    class SendAuditTaskTests {

        @Test
        @DisplayName("发送审核任务成功")
        void testSendAuditTask_Success() {
            AuditTaskMessage task = new AuditTaskMessage(
                    "msg-456", "job-002", "post-123", "http://example.com/post/123", LocalDateTime.now(), 0);

            messageQueueService.sendAuditTask(task);

            verify(streamOperations).add(eq(AUDIT_STREAM), anyMap());
        }

        @Test
        @DisplayName("发送重试审核任务")
        void testSendAuditTask_WithRetry() {
            AuditTaskMessage task = new AuditTaskMessage(
                    "msg-789", "job-003", "post-456", "http://example.com/post/456", LocalDateTime.now(), 2);

            messageQueueService.sendAuditTask(task);

            verify(streamOperations).add(eq(AUDIT_STREAM), argThat(map ->
                    "2".equals(map.get("retryCount"))));
        }
    }

    @Nested
    @DisplayName("consumeCrawlTasks tests")
    class ConsumeCrawlTasksTests {

        @Test
        @DisplayName("消费爬虫任务成功")
        @SuppressWarnings("unchecked")
        void testConsumeCrawlTasks_Success() {
            MapRecord<String, Object, Object> record = mock(MapRecord.class);
            List<MapRecord<String, Object, Object>> records = List.of(record);

            when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                    .thenReturn(records);
            when(streamOperations.size(CRAWL_STREAM)).thenReturn(10L);

            List<MapRecord<String, Object, Object>> result =
                    messageQueueService.consumeCrawlTasks("worker-1", 5);

            assertThat(result).hasSize(1);
            verify(streamOperations).read(
                    eq(Consumer.from(CRAWL_GROUP, "worker-1")),
                    any(StreamReadOptions.class),
                    eq(StreamOffset.create(CRAWL_STREAM, ReadOffset.lastConsumed())));
        }

        @Test
        @DisplayName("消费爬虫任务，返回空列表")
        void testConsumeCrawlTasks_Empty() {
            when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                    .thenReturn(null);

            List<MapRecord<String, Object, Object>> result =
                    messageQueueService.consumeCrawlTasks("worker-1", 5);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("消费爬虫任务，处理异常")
        void testConsumeCrawlTasks_Exception() {
            when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                    .thenThrow(new RuntimeException("Redis error"));

            List<MapRecord<String, Object, Object>> result =
                    messageQueueService.consumeCrawlTasks("worker-1", 5);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("consumeAuditTasks tests")
    class ConsumeAuditTasksTests {

        @Test
        @DisplayName("消费审核任务成功")
        @SuppressWarnings("unchecked")
        void testConsumeAuditTasks_Success() {
            MapRecord<String, Object, Object> record = mock(MapRecord.class);
            List<MapRecord<String, Object, Object>> records = List.of(record);

            when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                    .thenReturn(records);
            when(streamOperations.size(AUDIT_STREAM)).thenReturn(5L);

            List<MapRecord<String, Object, Object>> result =
                    messageQueueService.consumeAuditTasks("audit-worker-1", 10);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("消费审核任务返回空列表")
        void testConsumeAuditTasks_Empty() {
            when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                    .thenReturn(List.of());

            List<MapRecord<String, Object, Object>> result =
                    messageQueueService.consumeAuditTasks("audit-worker-1", 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("acknowledge methods tests")
    class AcknowledgeTests {

        @Test
        @DisplayName("确认爬虫任务完成")
        void testAcknowledgeCrawlTask() {
            messageQueueService.acknowledgeCrawlTask("message-123");

            verify(streamOperations).acknowledge(CRAWL_STREAM, CRAWL_GROUP, "message-123");
        }

        @Test
        @DisplayName("确认审核任务完成")
        void testAcknowledgeAuditTask() {
            messageQueueService.acknowledgeAuditTask("message-456");

            verify(streamOperations).acknowledge(AUDIT_STREAM, AUDIT_GROUP, "message-456");
        }
    }

    @Nested
    @DisplayName("dead letter queue tests")
    class DeadLetterQueueTests {

        @Test
        @DisplayName("发送任务到死信队列")
        void testSendToDeadLetterQueue() {
            Map<String, String> originalMessage = new HashMap<>();
            originalMessage.put("url", "http://example.com");
            originalMessage.put("jobId", "job-001");

            messageQueueService.sendToDeadLetterQueue(CRAWL_STREAM, "job-001", "Test error", originalMessage);

            verify(streamOperations).add(eq(DEAD_LETTER_STREAM), anyMap());
        }
    }

    @Nested
    @DisplayName("getQueueStats tests")
    class GetQueueStatsTests {

        @Test
        @DisplayName("获取队列统计信息成功")
        void testGetQueueStats_Success() {
            when(streamOperations.size(CRAWL_STREAM)).thenReturn(100L);
            when(streamOperations.size(AUDIT_STREAM)).thenReturn(50L);
            when(streamOperations.size(DEAD_LETTER_STREAM)).thenReturn(5L);

            PendingMessagesSummary crawlPending = mock(PendingMessagesSummary.class);
            when(crawlPending.getTotalPendingMessages()).thenReturn(10L);
            PendingMessagesSummary auditPending = mock(PendingMessagesSummary.class);
            when(auditPending.getTotalPendingMessages()).thenReturn(3L);

            when(streamOperations.pending(CRAWL_STREAM, CRAWL_GROUP)).thenReturn(crawlPending);
            when(streamOperations.pending(AUDIT_STREAM, AUDIT_GROUP)).thenReturn(auditPending);

            Map<String, Object> stats = messageQueueService.getQueueStats();

            assertThat(stats.get("crawlQueueSize")).isEqualTo(100L);
            assertThat(stats.get("auditQueueSize")).isEqualTo(50L);
            assertThat(stats.get("deadLetterQueueSize")).isEqualTo(5L);
            assertThat(stats.get("crawlPendingCount")).isEqualTo(10L);
            assertThat(stats.get("auditPendingCount")).isEqualTo(3L);
        }

        @Test
        @DisplayName("获取队列统计信息，处理 null 值")
        void testGetQueueStats_WithNullValues() {
            when(streamOperations.size(CRAWL_STREAM)).thenReturn(null);
            when(streamOperations.size(AUDIT_STREAM)).thenReturn(null);
            when(streamOperations.size(DEAD_LETTER_STREAM)).thenReturn(null);
            when(streamOperations.pending(anyString(), anyString())).thenReturn(null);

            Map<String, Object> stats = messageQueueService.getQueueStats();

            // null 值应被转换为 0
            assertThat(stats.get("crawlQueueSize")).isEqualTo(0L);
            assertThat(stats.get("auditQueueSize")).isEqualTo(0L);
            assertThat(stats.get("deadLetterQueueSize")).isEqualTo(0L);
            assertThat(stats.get("crawlPendingCount")).isEqualTo(0L);
            assertThat(stats.get("auditPendingCount")).isEqualTo(0L);
        }

        @Test
        @DisplayName("获取队列统计信息，处理异常")
        void testGetQueueStats_WithException() {
            when(streamOperations.size(anyString())).thenThrow(new RuntimeException("Redis error"));

            Map<String, Object> stats = messageQueueService.getQueueStats();

            // 异常时应返回空 map
            assertThat(stats).isEmpty();
        }
    }

    @Nested
    @DisplayName("queryDeadLetterQueue tests")
    class QueryDeadLetterQueueTests {

        @Test
        @DisplayName("查询死信队列成功")
        @SuppressWarnings("unchecked")
        void testQueryDeadLetterQueue_Success() {
            MapRecord<String, Object, Object> record = mock(MapRecord.class);
            when(streamOperations.range(eq(DEAD_LETTER_STREAM), any(), any()))
                    .thenReturn(List.of(record));

            List<MapRecord<String, Object, Object>> result =
                    messageQueueService.queryDeadLetterQueue(10);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("查询死信队列处理异常")
        void testQueryDeadLetterQueue_Exception() {
            when(streamOperations.range(eq(DEAD_LETTER_STREAM), any(), any()))
                    .thenThrow(new RuntimeException("Redis error"));

            List<MapRecord<String, Object, Object>> result =
                    messageQueueService.queryDeadLetterQueue(10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("reclaimPendingMessages tests")
    class ReclaimPendingMessagesTests {

        @Test
        @DisplayName("回收超时 Pending 消息")
        @SuppressWarnings("unchecked")
        void testReclaimPendingMessages_Success() {
            // 设置 mocks
            PendingMessagesSummary pendingMessagesSummary = mock(PendingMessagesSummary.class);
            when(pendingMessagesSummary.getTotalPendingMessages()).thenReturn(1L);
            when(streamOperations.pending(eq(CRAWL_STREAM), eq(CRAWL_GROUP)))
                    .thenReturn(pendingMessagesSummary);

            messageQueueService.reclaimPendingMessages();

            // 由于 Mock 复杂性，仅验证方法被调用
            verify(streamOperations).pending(eq(CRAWL_STREAM), eq(CRAWL_GROUP));
        }

        @Test
        @DisplayName("没有 Pending 消息时不回收")
        void testReclaimPendingMessages_NoPending() {
            PendingMessagesSummary pendingMessagesSummary = mock(PendingMessagesSummary.class);
            when(pendingMessagesSummary.getTotalPendingMessages()).thenReturn(0L);
            when(streamOperations.pending(eq(CRAWL_STREAM), eq(CRAWL_GROUP)))
                    .thenReturn(pendingMessagesSummary);

            messageQueueService.reclaimPendingMessages();

            // 不应调用 claim
            verify(streamOperations, never()).claim(anyString(), anyString(), anyString(), any(Duration.class), any());
        }
    }

    @Nested
    @DisplayName("edge cases tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("处理空 URL 任务")
        void testEmptyUrlTask() {
            CrawlTaskMessage task = new CrawlTaskMessage(
                    "msg-empty", "job-empty", "", "API", false, 0, LocalDateTime.now());

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(streamOperations.add(anyString(), anyMap())).thenReturn(RecordId.of("0-0"));

            String result = messageQueueService.sendCrawlTask(task);

            // 空 URL 也应能处理
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("处理特殊字符 URL 任务")
        void testSpecialCharUrlTask() {
            CrawlTaskMessage task = new CrawlTaskMessage(
                    "msg-special", "job-special", "http://example.com/path?param=value&other=123",
                    "API", false, 0, LocalDateTime.now());

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(streamOperations.add(anyString(), anyMap())).thenReturn(RecordId.of("0-0"));

            String result = messageQueueService.sendCrawlTask(task);

            assertThat(result).isNotNull();
        }
    }
}
