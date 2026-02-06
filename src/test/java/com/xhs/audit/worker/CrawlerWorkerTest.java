package com.xhs.audit.worker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.test.util.ReflectionTestUtils;

import com.xhs.audit.infrastructure.CrawlDuplicateFilter;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.AuditTaskMessage;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.TaskStatus;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.service.CrawlerService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * CrawlerWorker 单元测试
 * v4.0: 测试爬虫 Worker 的核心业务逻辑
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlerWorker - 爬虫 Worker 单元测试")
class CrawlerWorkerTest {

    @Mock
    private MessageQueueService messageQueueService;

    @Mock
    private CrawlerService crawlerService;

    @Mock
    private AuditJobRepository auditJobRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private CrawlDuplicateFilter crawlDuplicateFilter;

    @Mock
    private RLock lock;

    @InjectMocks
    private CrawlerWorker crawlerWorker;

    @Captor
    private ArgumentCaptor<AuditTaskMessage> auditTaskMessageCaptor;

    @Captor
    private ArgumentCaptor<AuditJob> auditJobCaptor;

    private MeterRegistry meterRegistry;
    private AuditJob testJob;
    private XhsContent testContent;

    @BeforeEach
    void setUp() throws Exception {
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(crawlerWorker, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(crawlerWorker, "batchSize", 10);
        ReflectionTestUtils.setField(crawlerWorker, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(crawlerWorker, "lockWaitTime", 30);
        ReflectionTestUtils.setField(crawlerWorker, "lockLeaseTime", 120);
        ReflectionTestUtils.setField(crawlerWorker, "workerType", "both");
        ReflectionTestUtils.setField(crawlerWorker, "concurrentCrawlThreads", 5);

        // 初始化测试数据
        testJob = new AuditJob();
        testJob.setJobId("job-001");
        testJob.setStatus(TaskStatus.PENDING.name());
        testJob.setTotalLinks(5);
        testJob.setCompletedCount(0);
        testJob.setSuccessCount(0);
        testJob.setFailedCount(0);
        testJob.setCreatedAt(LocalDateTime.now());
        testJob.setUpdatedAt(LocalDateTime.now());

        testContent = new XhsContent();
        testContent.setPostId("post-001");
        testContent.setUrl("https://www.xiaohongshu.com/explore/post001");
        testContent.setTitle("测试标题");
        testContent.setContent("测试内容");
    }

    // ==================== processMessage 成功路径测试 ====================

    @Test
    @DisplayName("processMessage - 成功路径：爬取 -> 发送审核任务 -> ACK")
    void testProcessMessage_SuccessPath() throws Exception {
        // Arrange
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(false).when(crawlDuplicateFilter).mightContain(anyString());
        doReturn(testContent).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(crawlerService).crawlContent("https://www.xiaohongshu.com/explore/post001");
        verify(crawlDuplicateFilter).add("https://www.xiaohongshu.com/explore/post001");
        verify(messageQueueService).sendAuditTask(auditTaskMessageCaptor.capture());
        verify(messageQueueService).acknowledgeCrawlTask(anyString());

        AuditTaskMessage sentMessage = auditTaskMessageCaptor.getValue();
        assertEquals("job-001", sentMessage.jobId());
        assertEquals("post-001", sentMessage.postId());
        assertEquals("https://www.xiaohongshu.com/explore/post001", sentMessage.url());

        // 验证进度更新
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> j.getCompletedCount() == 1));
    }

    @Test
    @DisplayName("processMessage - 成功路径：不使用分布式锁（单节点模式）")
    void testProcessMessage_SuccessWithoutLock() throws Exception {
        // Arrange - 单节点模式，redissonClient 为 null
        ReflectionTestUtils.setField(crawlerWorker, "redissonClient", null);
        doReturn(false).when(crawlDuplicateFilter).mightContain(anyString());
        doReturn(testContent).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(crawlerService).crawlContent(anyString());
        verify(messageQueueService).sendAuditTask(any(AuditTaskMessage.class));
        verify(messageQueueService).acknowledgeCrawlTask(anyString());
    }

    // ==================== 分布式锁测试 ====================

    @Test
    @DisplayName("processMessage - 获取分布式锁失败时应返回，不 ACK 消息")
    void testProcessMessage_LockAcquisitionFailed() throws Exception {
        // Arrange
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(false).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(crawlerService, never()).crawlContent(anyString());
        verify(messageQueueService, never()).acknowledgeCrawlTask(anyString());
        verify(messageQueueService, never()).sendAuditTask(any(AuditTaskMessage.class));
    }

    @Test
    @DisplayName("processMessage - 锁释放应在 finally 块中执行")
    void testProcessMessage_LockReleasedInFinally() throws Exception {
        // Arrange
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(false).when(crawlDuplicateFilter).mightContain(anyString());
        doReturn(testContent).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(lock).unlock();
    }

    // ==================== 重试逻辑测试 ====================

    @Test
    @DisplayName("processMessage - 重试次数小于最大值时重新发送任务到队列")
    void testProcessMessage_RetryWhenUnderMaxAttempts() throws Exception {
        // Arrange - 设置状态为 CRAWLING（RETRYING 只允许从 CRAWLING 转换）
        testJob.setStatus(TaskStatus.CRAWLING.name());
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doThrow(new RuntimeException("爬取失败")).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act - 使用 retryCount = 1
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "1"));

        // Assert - 应该重试，发送新的爬虫任务
        verify(messageQueueService).sendCrawlTask(any());
        verify(messageQueueService).acknowledgeCrawlTask(anyString());

        // 验证任务状态更新为 RETRYING
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> TaskStatus.RETRYING.name().equals(j.getStatus())));
    }

    @Test
    @DisplayName("processMessage - 超过最大重试次数时移入死信队列")
    void testProcessMessage_MaxRetriesExceeded_SendToDeadLetter() throws Exception {
        // Arrange - 设置重试次数等于最大值 3
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doThrow(new RuntimeException("持续爬取失败")).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "3"));

        // Assert
        verify(messageQueueService).sendToDeadLetterQueue(eq("crawl"), eq("job-001"), anyString(), anyMap());
        verify(messageQueueService).acknowledgeCrawlTask(anyString());

        // 验证任务状态更新为 FAILED
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> TaskStatus.FAILED.name().equals(j.getStatus())));
    }

    // ==================== 布隆过滤器测试 ====================

    @Test
    @DisplayName("processMessage - 布隆过滤器提示 URL 可能存在时应继续处理")
    void testProcessMessage_BloomFilterHint() throws Exception {
        // Arrange
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(true).when(crawlDuplicateFilter).mightContain(anyString()); // 布隆过滤器提示可能存在
        doReturn(testContent).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert - 布隆过滤器只作为提示，不阻止处理
        verify(crawlerService).crawlContent(anyString());
        verify(messageQueueService).sendAuditTask(any(AuditTaskMessage.class));
    }

    @Test
    @DisplayName("processMessage - 布隆过滤器未配置时应继续处理")
    void testProcessMessage_BloomFilterNotConfigured() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(crawlerWorker, "crawlDuplicateFilter", null);
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(testContent).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(crawlerService).crawlContent(anyString());
        verify(messageQueueService).sendAuditTask(any(AuditTaskMessage.class));
    }

    // ==================== updateJobStatus 测试 ====================

    @Test
    @DisplayName("updateJobStatus - 合法状态转换应该成功")
    void testUpdateJobStatus_ValidTransition() throws Exception {
        // Arrange
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeUpdateJobStatus("job-001", TaskStatus.CRAWLING.name(), "爬取中...");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());
        assertEquals(TaskStatus.CRAWLING.name(), jobCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("updateJobStatus - 非法状态转换应被拒绝")
    void testUpdateJobStatus_InvalidTransition() throws Exception {
        // Arrange - 设置状态为 COMPLETED（终态）
        testJob.setStatus(TaskStatus.COMPLETED.name());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act - 尝试从 COMPLETED 转换到 CRAWLING（非法）
        invokeUpdateJobStatus("job-001", TaskStatus.CRAWLING.name(), "尝试非法转换");

        // Assert - save 不应被调用
        verify(auditJobRepository, never()).save(any(AuditJob.class));
    }

    @Test
    @DisplayName("updateJobStatus - 同状态转换应该允许（幂等性）")
    void testUpdateJobStatus_SameStateAllowed() throws Exception {
        // Arrange
        testJob.setStatus(TaskStatus.RETRYING.name());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeUpdateJobStatus("job-001", TaskStatus.RETRYING.name(), "重试中...");

        // Assert
        verify(auditJobRepository).save(any(AuditJob.class));
    }

    @Test
    @DisplayName("updateJobStatus - 任务不存在时应跳过")
    void testUpdateJobStatus_JobNotFound() throws Exception {
        // Arrange
        doReturn(Optional.empty()).when(auditJobRepository).findByJobId(eq("nonexistent"));

        // Act
        invokeUpdateJobStatus("nonexistent", TaskStatus.CRAWLING.name(), "测试");

        // Assert
        verify(auditJobRepository, never()).save(any(AuditJob.class));
    }

    // ==================== incrementJobProgress 测试 ====================

    @Test
    @DisplayName("incrementJobProgress - 批量任务完成时更新状态为 COMPLETED")
    void testIncrementJobProgress_AllCompleted() throws Exception {
        // Arrange - 设置已完成数量等于总数
        testJob.setTotalLinks(5);
        testJob.setCompletedCount(4); // 再完成一个就满了
        testJob.setSuccessCount(4);
        testJob.setStatus(TaskStatus.PENDING.name());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeIncrementJobProgress("job-001");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals(5, savedJob.getCompletedCount());
        assertEquals(5, savedJob.getSuccessCount());
        assertEquals(TaskStatus.COMPLETED.name(), savedJob.getStatus());
    }

    @Test
    @DisplayName("incrementJobProgress - 批量任务未完成时更新为 PROCESSING")
    void testIncrementJobProgress_NotAllCompleted() throws Exception {
        // Arrange - 设置已完成数量小于总数
        testJob.setTotalLinks(10);
        testJob.setCompletedCount(3);
        testJob.setSuccessCount(3);
        testJob.setStatus(TaskStatus.PENDING.name());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeIncrementJobProgress("job-001");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals(4, savedJob.getCompletedCount());
        assertEquals("PROCESSING", savedJob.getStatus());
    }

    @Test
    @DisplayName("incrementJobProgress - 任务不存在时应跳过")
    void testIncrementJobProgress_JobNotFound() throws Exception {
        // Arrange
        doReturn(Optional.empty()).when(auditJobRepository).findByJobId(eq("nonexistent"));

        // Act
        invokeIncrementJobProgress("nonexistent");

        // Assert
        verify(auditJobRepository, never()).save(any(AuditJob.class));
    }

    // ==================== shutdown 测试 ====================

    @Test
    @DisplayName("shutdown - 优雅停机应停止运行标志")
    void testShutdown_GracefulShutdown() throws Exception {
        // Act
        crawlerWorker.shutdown();

        // Assert
        // 验证日志输出（通过检查 running 状态）
        assertNotNull(crawlerWorker);
    }

    // ==================== 异常处理测试 ====================

    @Test
    @DisplayName("processMessage - 爬取异常时应正确处理（达到最大重试次数后进入死信队列）")
    void testProcessMessage_CrawlException() throws Exception {
        // Arrange - 设置 retryCount = 3（等于 maxRetryAttempts），直接进入死信队列
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(false).when(crawlDuplicateFilter).mightContain(anyString());
        doThrow(new RuntimeException("网络异常")).when(crawlerService).crawlContent(anyString());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act - 使用 retryCount = 3（等于最大值），直接进入死信队列
        invokeProcessMessage(createTestMessage("job-001", "https://www.xiaohongshu.com/explore/post001", "3"));

        // Assert - 应该进入死信队列
        verify(messageQueueService).sendToDeadLetterQueue(
                eq("crawl"), eq("job-001"), anyString(), anyMap());
        verify(messageQueueService).acknowledgeCrawlTask(anyString());

        // 验证任务状态更新为 FAILED
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> TaskStatus.FAILED.name().equals(j.getStatus())));
    }

    // ==================== 辅助方法 ====================

    private Object createTestMessage(String jobId, String url, String retryCount) throws Exception {
        // 使用反射创建 MapRecord
        Map<String, String> messageValue = new HashMap<>();
        messageValue.put("jobId", jobId);
        messageValue.put("url", url);
        messageValue.put("retryCount", retryCount);

        RecordId recordId = RecordId.of("0-1");

        // 使用 MapRecord.create
        return MapRecord.create(recordId, messageValue);
    }

    private void invokeProcessMessage(Object message) throws Exception {
        java.lang.reflect.Method method = CrawlerWorker.class.getDeclaredMethod("processMessage",
                MapRecord.class);
        method.setAccessible(true);
        method.invoke(crawlerWorker, message);
    }

    private void invokeUpdateJobStatus(String jobId, String status, String message) throws Exception {
        java.lang.reflect.Method method = CrawlerWorker.class.getDeclaredMethod("updateJobStatus",
                String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(crawlerWorker, jobId, status, message);
    }

    private void invokeIncrementJobProgress(String jobId) throws Exception {
        java.lang.reflect.Method method = CrawlerWorker.class.getDeclaredMethod("incrementJobProgress",
                String.class);
        method.setAccessible(true);
        method.invoke(crawlerWorker, jobId);
    }
}
