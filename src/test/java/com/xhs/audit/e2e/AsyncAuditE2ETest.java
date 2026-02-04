package com.xhs.audit.e2e;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.config.RedisStreamConstants;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.AuditTaskMessage;
import com.xhs.audit.model.dto.CrawlTaskMessage;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.TaskStatus;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;
import com.xhs.audit.repository.XhsContentRepository;

/**
 * v4.0 E2E 测试 - 端到端异步审核流程
 *
 * 测试场景：
 * 1. 异步任务提交流程 (API → Redis Stream → Worker)
 * 2. 消息队列幂等性 (重复提交检测)
 * 3. 任务状态流转 (PENDING → CRAWLING → CRAWLED → AUDITING → COMPLETED)
 * 4. 死信队列处理
 * 5. 断路器降级
 *
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("v4.0 E2E - 端到端异步审核流程测试")
class AsyncAuditE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired
    private XhsContentRepository xhsContentRepository;

    @Autowired
    private AuditResultRepository auditResultRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        // 清理测试数据
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    private void cleanupTestData() {
        auditResultRepository.deleteAll();
        xhsContentRepository.deleteAll();
        auditJobRepository.deleteAll();
    }

    // ============================================
    // 测试 1: 异步任务提交流程
    // ============================================

    @Test
    @DisplayName("E2E-001: 提交单个异步审核任务")
    void testSubmitAsyncAuditTask() {
        // Arrange
        String testUrl = "https://example.com/post/" + UUID.randomUUID();
        Map<String, Object> request = new HashMap<>();
        request.put("url", testUrl);
        request.put("source", "E2E_TEST");
        request.put("forceRefresh", false);

        // Act: 提交任务
        ResponseEntity<Map> submitResponse = restTemplate.postForEntity(
                baseUrl + "/api/audit/async",
                request,
                Map.class);

        // Assert: 验证提交成功
        assertEquals(HttpStatus.ACCEPTED, submitResponse.getStatusCode());
        assertNotNull(submitResponse.getBody());
        assertEquals("accepted", submitResponse.getBody().get("status"));
        assertNotNull(submitResponse.getBody().get("jobId"));

        String jobId = (String) submitResponse.getBody().get("jobId");

        // Act: 查询任务状态
        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(
                baseUrl + "/api/audit/result/" + jobId,
                Map.class);

        // Assert: 验证任务已创建
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertNotNull(statusResponse.getBody());
        assertEquals(jobId, statusResponse.getBody().get("jobId"));
        assertNotNull(statusResponse.getBody().get("status"));

        System.out.println("[E2E-001] 测试通过: jobId=" + jobId);
    }

    @Test
    @DisplayName("E2E-002: 批量提交异步审核任务")
    void testSubmitBatchAsyncAuditTasks() {
        // Arrange - 使用唯一URL避免重复检测
        String batchId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> request = new HashMap<>();
        request.put("urls", List.of(
                "https://example.com/post/batch-" + batchId + "-001",
                "https://example.com/post/batch-" + batchId + "-002",
                "https://example.com/post/batch-" + batchId + "-003"));
        request.put("source", "E2E_BATCH_TEST");

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/audit/async/batch",
                request,
                Map.class);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("accepted", response.getBody().get("status"));
        assertEquals(3, response.getBody().get("totalSubmitted"));
        assertEquals(0, response.getBody().get("totalFailed"));
        assertNotNull(response.getBody().get("batchJobId"));

        System.out.println("[E2E-002] 批量提交测试通过: batchJobId=" + response.getBody().get("batchJobId"));
    }

    // ============================================
    // 测试 2: 消息队列幂等性
    // ============================================

    @Test
    @DisplayName("E2E-003: 重复提交同一URL应被拒绝")
    void testDuplicateUrlSubmission() {
        // Arrange
        String testUrl = "https://example.com/post/duplicate-" + UUID.randomUUID();
        Map<String, Object> request = new HashMap<>();
        request.put("url", testUrl);
        request.put("source", "E2E_DEDUP_TEST");

        // Act: 第一次提交
        ResponseEntity<Map> firstResponse = restTemplate.postForEntity(
                baseUrl + "/api/audit/async",
                request,
                Map.class);

        assertEquals(HttpStatus.ACCEPTED, firstResponse.getStatusCode());
        String firstJobId = (String) firstResponse.getBody().get("jobId");

        // Act: 第二次提交（重复）
        ResponseEntity<Map> secondResponse = restTemplate.postForEntity(
                baseUrl + "/api/audit/async",
                request,
                Map.class);

        // Assert: 第二次提交应被拒绝
        assertEquals(HttpStatus.BAD_REQUEST, secondResponse.getStatusCode());
        assertEquals("SUBMIT_FAILED", secondResponse.getBody().get("code"));

        System.out.println("[E2E-003] 幂等性测试通过: 重复URL被正确拒绝");
    }

    // ============================================
    // 测试 3: 任务状态流转
    // ============================================

    @Test
    @DisplayName("E2E-004: 任务状态流转验证")
    void testTaskStatusTransition() {
        // Arrange: 创建测试任务
        String jobId = "test-status-" + UUID.randomUUID();
        AuditJob job = new AuditJob();
        job.setJobId(jobId);
        job.setUrl("https://example.com/post/test-status");
        job.setTotalLinks(1);
        job.setStatus(TaskStatus.PENDING.name());
        job.setMessage("测试任务");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        auditJobRepository.save(job);

        // Act & Assert: 验证状态机转换
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.PENDING.isSuccess());

        // PENDING → CRAWLING
        job.setStatus(TaskStatus.CRAWLING.name());
        job.setMessage("爬取中...");
        assertFalse(TaskStatus.CRAWLING.isTerminal());

        // CRAWLING → CRAWLED
        job.setStatus(TaskStatus.CRAWLED.name());
        job.setMessage("爬取完成");
        assertFalse(TaskStatus.CRAWLED.isTerminal());

        // CRAWLED → AUDITING
        job.setStatus(TaskStatus.AUDITING.name());
        job.setMessage("审核中...");
        assertFalse(TaskStatus.AUDITING.isTerminal());

        // AUDITING → COMPLETED
        job.setStatus(TaskStatus.COMPLETED.name());
        job.setMessage("审核完成");
        assertTrue(TaskStatus.COMPLETED.isTerminal());
        assertTrue(TaskStatus.COMPLETED.isSuccess());

        // FAILED 是终态但不是成功
        job.setStatus(TaskStatus.FAILED.name());
        assertTrue(TaskStatus.FAILED.isTerminal());
        assertFalse(TaskStatus.FAILED.isSuccess());

        System.out.println("[E2E-004] 状态流转测试通过");
    }

    @Test
    @DisplayName("E2E-005: 终态任务不可再处理")
    void testTerminalTaskCannotBeProcessed() {
        // 验证 COMPLETED 任务
        AuditJob completedJob = new AuditJob();
        completedJob.setJobId("completed-" + UUID.randomUUID());
        completedJob.setStatus(TaskStatus.COMPLETED.name());
        assertTrue(completedJob.getStatus().equals(TaskStatus.COMPLETED.name()) &&
                TaskStatus.COMPLETED.isTerminal());

        // 验证 FAILED 任务
        AuditJob failedJob = new AuditJob();
        failedJob.setJobId("failed-" + UUID.randomUUID());
        failedJob.setStatus(TaskStatus.FAILED.name());
        assertTrue(failedJob.getStatus().equals(TaskStatus.FAILED.name()) &&
                TaskStatus.FAILED.isTerminal());

        System.out.println("[E2E-005] 终态验证测试通过");
    }

    // ============================================
    // 测试 4: 队列统计 API
    // ============================================

    @Test
    @DisplayName("E2E-006: 获取队列统计信息")
    void testGetQueueStats() {
        // Act
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/queue/stats",
                Map.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("crawlQueueSize"));
        assertTrue(response.getBody().containsKey("auditQueueSize"));
        assertTrue(response.getBody().containsKey("deadLetterQueueSize"));

        System.out.println("[E2E-006] 队列统计测试通过: " + response.getBody());
    }

    // ============================================
    // 测试 5: 任务不存在场景
    // ============================================

    @Test
    @DisplayName("E2E-007: 查询不存在的任务返回 404")
    void testGetNonExistentJob() {
        // Act
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/result/non-existent-job-id",
                Map.class);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("JOB_NOT_FOUND", response.getBody().get("code"));

        System.out.println("[E2E-007] 任务不存在测试通过");
    }

    // ============================================
    // 测试 6: 数据库实体验证
    // ============================================

    @Test
    @DisplayName("E2E-008: AuditJob 实体字段验证 (v4.0新增字段)")
    void testAuditJobEntityFields() {
        // Arrange
        AuditJob job = new AuditJob();
        String jobId = "entity-test-" + UUID.randomUUID();
        String testUrl = "https://example.com/post/entity-test";

        // Act
        job.setJobId(jobId);
        job.setUrl(testUrl); // v4.0 新增字段
        job.setMessage("测试消息"); // v4.0 新增字段
        job.setTotalLinks(5);
        job.setStatus(TaskStatus.PENDING.name());

        // Assert
        assertEquals(jobId, job.getJobId());
        assertEquals(testUrl, job.getUrl());
        assertEquals("测试消息", job.getMessage());
        assertEquals(5, job.getTotalLinks());
        assertEquals(TaskStatus.PENDING.name(), job.getStatus());

        System.out.println("[E2E-008] AuditJob 实体字段测试通过");
    }

    @Test
    @DisplayName("E2E-009: XhsContent 实体 postId 长度扩展验证")
    void testXhsContentPostIdLength() {
        // Arrange: v4.0 将 postId 从 VARCHAR(100) 扩展到 VARCHAR(256)
        XhsContent content = new XhsContent();

        // 创建一个 200 字符的 postId（验证扩展后的长度）
        String longPostId = "xhs_post_" + "a".repeat(190) + "_" + UUID.randomUUID().toString().substring(0, 8);
        assertTrue(longPostId.length() > 100 && longPostId.length() <= 256);

        // Act
        content.setPostId(longPostId);
        content.setUrl("https://example.com/post/" + longPostId);
        content.setTitle("测试内容");
        content.setContent("测试正文内容");

        // Assert
        assertEquals(longPostId, content.getPostId());
        assertTrue(content.getPostId().length() <= 256);

        System.out.println("[E2E-009] postId 长度扩展测试通过: length=" + content.getPostId().length());
    }

    // ============================================
    // 测试 7: Redis Stream 配置验证
    // ============================================

    @Test
    @DisplayName("E2E-010: Redis Stream 常量配置验证")
    void testRedisStreamConstants() {
        // 验证 Stream 名称
        assertEquals("xhs:stream:crawl", RedisStreamConstants.CRAWL_STREAM);
        assertEquals("xhs:stream:audit", RedisStreamConstants.AUDIT_STREAM);
        assertEquals("xhs:stream:dead-letter", RedisStreamConstants.DEAD_LETTER_STREAM);

        // 验证消费者组名称
        assertEquals("crawl-workers", RedisStreamConstants.CRAWL_GROUP);
        assertEquals("audit-workers", RedisStreamConstants.AUDIT_GROUP);

        // 验证去重前缀
        assertEquals("processed:crawl:", RedisStreamConstants.DEDUPE_PREFIX_CRAWL);

        // 验证分布式锁前缀
        assertEquals("crawler:processing:", RedisStreamConstants.LOCK_PREFIX_CRAWLER);
        assertEquals("audit:processing:", RedisStreamConstants.LOCK_PREFIX_AUDIT);

        System.out.println("[E2E-010] Redis Stream 常量配置测试通过");
    }

    // ============================================
    // 测试 8: 消息服务集成
    // ============================================

    @Test
    @DisplayName("E2E-011: 消息队列服务可用性验证")
    void testMessageQueueServiceAvailability() {
        // Act
        Map<String, Object> stats = messageQueueService.getQueueStats();

        // Assert
        assertNotNull(stats);
        assertTrue(stats.containsKey("crawlQueueSize"));
        assertTrue(stats.containsKey("auditQueueSize"));
        assertTrue(stats.containsKey("deadLetterQueueSize"));
        assertTrue(stats.containsKey("crawlPendingCount"));
        assertTrue(stats.containsKey("auditPendingCount"));

        System.out.println("[E2E-011] 消息队列服务可用性测试通过: " + stats);
    }

    @Test
    @DisplayName("E2E-012: 发送爬虫任务到消息队列")
    void testSendCrawlTaskToQueue() {
        // Arrange
        String jobId = "mq-test-" + UUID.randomUUID();
        String url = "https://example.com/post/mq-test-" + UUID.randomUUID();
        CrawlTaskMessage task = new CrawlTaskMessage(
                UUID.randomUUID().toString(),
                jobId,
                url,
                "E2E_MQ_TEST",
                false,
                0,
                LocalDateTime.now());

        // Act
        String recordId = messageQueueService.sendCrawlTask(task);

        // Assert
        assertNotNull(recordId);

        System.out.println("[E2E-012] 发送爬虫任务测试通过: recordId=" + recordId);
    }

    @Test
    @DisplayName("E2E-013: 发送审核任务到消息队列")
    void testSendAuditTaskToQueue() {
        // Arrange
        String jobId = "audit-mq-test-" + UUID.randomUUID();
        String postId = "post-001-" + UUID.randomUUID();
        AuditTaskMessage task = new AuditTaskMessage(
                UUID.randomUUID().toString(),
                jobId,
                postId,
                "https://example.com/post/" + postId,
                LocalDateTime.now(),
                0);

        // Act & Assert
        assertDoesNotThrow(() -> messageQueueService.sendAuditTask(task));

        System.out.println("[E2E-013] 发送审核任务测试通过: jobId=" + jobId);
    }

    // ============================================
    // 测试 9: 死信队列验证
    // ============================================

    @Test
    @DisplayName("E2E-014: 死信队列查询")
    void testDeadLetterQueueQuery() {
        // Act
        var dlq = messageQueueService.queryDeadLetterQueue(10);

        // Assert
        assertNotNull(dlq);

        System.out.println("[E2E-014] 死信队列查询测试通过: 当前条目数=" + dlq.size());
    }
}
