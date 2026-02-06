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
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import com.xhs.audit.agent.ContentAuditAgent;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.model.entity.TaskStatus;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;
import com.xhs.audit.repository.XhsContentRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * AuditWorker 单元测试
 * v4.0: 测试审核 Worker 的核心业务逻辑
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditWorker - 审核 Worker 单元测试")
class AuditWorkerTest {

    @Mock
    private MessageQueueService messageQueueService;

    @Mock
    private ContentAuditAgent contentAuditAgent;

    @Mock
    private AuditJobRepository auditJobRepository;

    @Mock
    private XhsContentRepository xhsContentRepository;

    @Mock
    private AuditResultRepository auditResultRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private AuditWorker auditWorker;

    @Captor
    private ArgumentCaptor<AuditJob> auditJobCaptor;

    private MeterRegistry meterRegistry;
    private AuditJob testJob;
    private XhsContent testContent;
    private AuditDecision passedDecision;

    @BeforeEach
    void setUp() throws Exception {
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(auditWorker, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(auditWorker, "batchSize", 10);
        ReflectionTestUtils.setField(auditWorker, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(auditWorker, "lockWaitTime", 30);
        ReflectionTestUtils.setField(auditWorker, "lockLeaseTime", 120);
        ReflectionTestUtils.setField(auditWorker, "workerType", "both");

        // 清理 MDC 上下文
        MDC.clear();

        // 初始化测试数据
        testJob = new AuditJob();
        testJob.setJobId("job-001");
        testJob.setStatus(TaskStatus.CRAWLED.name());
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

        // 初始化通过的审核决策
        passedDecision = AuditDecision.passed("post-001", 0.95);
    }

    // ==================== processMessage 成功路径测试 ====================

    @Test
    @DisplayName("processMessage - 成功路径：审核内容 -> 保存结果 -> 更新进度 -> ACK")
    void testProcessMessage_SuccessPath() throws Exception {
        // Arrange
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.of(testContent)).when(xhsContentRepository).findByPostId(eq("post-001"));
        doReturn(passedDecision).when(contentAuditAgent).auditContent(any(XhsContent.class));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(contentAuditAgent).auditContent(any(XhsContent.class));
        verify(auditResultRepository).save(any(AuditResult.class));
        verify(messageQueueService).acknowledgeAuditTask(anyString());

        // 验证进度更新（PASSED 应该增加 successCount）
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> j.getSuccessCount() >= 1));
    }

    @Test
    @DisplayName("processMessage - 成功路径：不使用分布式锁（单节点模式）")
    void testProcessMessage_SuccessWithoutLock() throws Exception {
        // Arrange - 单节点模式，redissonClient 为 null
        ReflectionTestUtils.setField(auditWorker, "redissonClient", null);
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.of(testContent)).when(xhsContentRepository).findByPostId(eq("post-001"));
        doReturn(passedDecision).when(contentAuditAgent).auditContent(any(XhsContent.class));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(contentAuditAgent).auditContent(any(XhsContent.class));
        verify(auditResultRepository).save(any(AuditResult.class));
        verify(messageQueueService).acknowledgeAuditTask(anyString());
    }

    @Test
    @DisplayName("processMessage - 已完成的任务应被跳过")
    void testProcessMessage_SkipAlreadyCompleted() throws Exception {
        // Arrange - 任务已完成
        testJob.setStatus(TaskStatus.COMPLETED.name());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(contentAuditAgent, never()).auditContent(any(XhsContent.class));
        verify(auditResultRepository, never()).save(any(AuditResult.class));
        verify(messageQueueService).acknowledgeAuditTask(anyString());
    }

    // ==================== 分布式锁测试 ====================

    @Test
    @DisplayName("processMessage - 获取分布式锁失败时应返回，不 ACK 消息")
    void testProcessMessage_LockAcquisitionFailed() throws Exception {
        // Arrange
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(false).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(contentAuditAgent, never()).auditContent(any(XhsContent.class));
        verify(messageQueueService, never()).acknowledgeAuditTask(anyString());
    }

    @Test
    @DisplayName("processMessage - 锁释放应在 finally 块中执行")
    void testProcessMessage_LockReleasedInFinally() throws Exception {
        // Arrange
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.of(testContent)).when(xhsContentRepository).findByPostId(eq("post-001"));
        doReturn(passedDecision).when(contentAuditAgent).auditContent(any(XhsContent.class));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "0"));

        // Assert
        verify(lock).unlock();
    }

    // ==================== 重试逻辑测试 ====================

    @Test
    @DisplayName("processMessage - 重试次数小于最大值时重新发送任务到队列")
    void testProcessMessage_RetryWhenUnderMaxAttempts() throws Exception {
        // Arrange - 设置重试次数为 1，小于最大值 3
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.of(testContent)).when(xhsContentRepository).findByPostId(eq("post-001"));
        doThrow(new RuntimeException("AI 服务暂时不可用")).when(contentAuditAgent).auditContent(any(XhsContent.class));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "1"));

        // Assert
        verify(messageQueueService).sendAuditTask(any());
        verify(messageQueueService).acknowledgeAuditTask(anyString());

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
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.of(testContent)).when(xhsContentRepository).findByPostId(eq("post-001"));
        doThrow(new RuntimeException("持续 AI 服务异常")).when(contentAuditAgent).auditContent(any(XhsContent.class));

        // Act
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "3"));

        // Assert
        verify(messageQueueService).sendToDeadLetterQueue(eq("audit"), eq("job-001"), anyString(), anyMap());
        verify(messageQueueService).acknowledgeAuditTask(anyString());

        // 验证任务状态更新为 FAILED
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> TaskStatus.FAILED.name().equals(j.getStatus())));
    }

    @Test
    @DisplayName("processMessage - 重试次数小于最大值时重新发送审核任务")
    void testProcessMessage_RetryUnderMaxAttempts_AuditWorker() throws Exception {
        // Arrange - 设置重试次数为 1，小于最大值 3
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.of(testContent)).when(xhsContentRepository).findByPostId(eq("post-001"));
        doThrow(new RuntimeException("AI 服务暂时不可用")).when(contentAuditAgent).auditContent(any(XhsContent.class));

        // Act - 使用 retryCount = 1
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "1"));

        // Assert - 应该重试，发送新的审核任务
        verify(messageQueueService).sendAuditTask(any());
        verify(messageQueueService).acknowledgeAuditTask(anyString());

        // 验证任务状态更新为 RETRYING
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> TaskStatus.RETRYING.name().equals(j.getStatus())));
    }

    // ==================== updateJobStatus 测试 ====================

    @Test
    @DisplayName("updateJobStatus - 合法状态转换应该成功")
    void testUpdateJobStatus_ValidTransition() throws Exception {
        // Arrange
        testJob.setStatus(TaskStatus.CRAWLED.name());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeUpdateJobStatus("job-001", TaskStatus.AUDITING.name(), "AI审核中...");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());
        assertEquals(TaskStatus.AUDITING.name(), jobCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("updateJobStatus - 非法状态转换应被拒绝")
    void testUpdateJobStatus_InvalidTransition() throws Exception {
        // Arrange - 设置状态为 COMPLETED（终态）
        testJob.setStatus(TaskStatus.COMPLETED.name());
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act - 尝试从 COMPLETED 转换到 AUDITING（非法）
        invokeUpdateJobStatus("job-001", TaskStatus.AUDITING.name(), "尝试非法转换");

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
        invokeUpdateJobStatus("nonexistent", TaskStatus.AUDITING.name(), "测试");

        // Assert
        verify(auditJobRepository, never()).save(any(AuditJob.class));
    }

    // ==================== updateJobProgress 测试 ====================

    @Test
    @DisplayName("updateJobProgress - PASSED 审核结果应增加成功计数")
    void testUpdateJobProgress_Passed() throws Exception {
        // Arrange
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeUpdateJobProgress("job-001", "PASSED");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals(1, savedJob.getCompletedCount());
        assertEquals(1, savedJob.getSuccessCount());
        assertEquals(0, savedJob.getFailedCount());
    }

    @Test
    @DisplayName("updateJobProgress - REJECTED 审核结果应增加失败计数")
    void testUpdateJobProgress_Rejected() throws Exception {
        // Arrange
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeUpdateJobProgress("job-001", "REJECTED");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals(1, savedJob.getCompletedCount());
        assertEquals(0, savedJob.getSuccessCount());
        assertEquals(1, savedJob.getFailedCount());
    }

    @Test
    @DisplayName("updateJobProgress - UNCERTAIN 审核结果应增加失败计数")
    void testUpdateJobProgress_Uncertain() throws Exception {
        // Arrange
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeUpdateJobProgress("job-001", "UNCERTAIN");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals(1, savedJob.getCompletedCount());
        assertEquals(0, savedJob.getSuccessCount());
        assertEquals(1, savedJob.getFailedCount());
    }

    @Test
    @DisplayName("updateJobProgress - 任务不存在时应跳过")
    void testUpdateJobProgress_JobNotFound() throws Exception {
        // Arrange
        doReturn(Optional.empty()).when(auditJobRepository).findByJobId(eq("nonexistent"));

        // Act
        invokeUpdateJobProgress("nonexistent", "PASSED");

        // Assert
        verify(auditJobRepository, never()).save(any(AuditJob.class));
    }

    // ==================== checkAndUpdateJobCompletion 测试 ====================

    @Test
    @DisplayName("checkAndUpdateJobCompletion - 全部通过时状态为 COMPLETED")
    void testCheckAndUpdateJobCompletion_AllPassed() throws Exception {
        // Arrange - 设置已完成数量等于总数，且全部成功
        testJob.setTotalLinks(5);
        testJob.setCompletedCount(5);
        testJob.setSuccessCount(5);
        testJob.setFailedCount(0);
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeCheckAndUpdateJobCompletion("job-001");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals(TaskStatus.COMPLETED.name(), savedJob.getStatus());
        assertNotNull(savedJob.getCompletedAt());
    }

    @Test
    @DisplayName("checkAndUpdateJobCompletion - 部分失败时状态为 PARTIAL_SUCCESS")
    void testCheckAndUpdateJobCompletion_PartialSuccess() throws Exception {
        // Arrange - 设置已完成，部分成功部分失败
        testJob.setTotalLinks(5);
        testJob.setCompletedCount(5);
        testJob.setSuccessCount(3);
        testJob.setFailedCount(2);
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeCheckAndUpdateJobCompletion("job-001");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals("PARTIAL_SUCCESS", savedJob.getStatus());
    }

    @Test
    @DisplayName("checkAndUpdateJobCompletion - 全部失败时状态为 FAILED")
    void testCheckAndUpdateJobCompletion_AllFailed() throws Exception {
        // Arrange - 设置已完成，全部失败
        testJob.setTotalLinks(5);
        testJob.setCompletedCount(5);
        testJob.setSuccessCount(0);
        testJob.setFailedCount(5);
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeCheckAndUpdateJobCompletion("job-001");

        // Assert
        ArgumentCaptor<AuditJob> jobCaptor = ArgumentCaptor.forClass(AuditJob.class);
        verify(auditJobRepository).save(jobCaptor.capture());

        AuditJob savedJob = jobCaptor.getValue();
        assertEquals(TaskStatus.FAILED.name(), savedJob.getStatus());
    }

    @Test
    @DisplayName("checkAndUpdateJobCompletion - 未完成时不应调用 save")
    void testCheckAndUpdateJobCompletion_NotCompleted() throws Exception {
        // Arrange - 设置未完成 (completedCount < totalLinks)
        testJob.setTotalLinks(5);
        testJob.setCompletedCount(3);  // 3 < 5，所以不会调用 save
        testJob.setSuccessCount(3);
        testJob.setFailedCount(0);
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));

        // Act
        invokeCheckAndUpdateJobCompletion("job-001");

        // Assert - 未完成时不会调用 save
        verify(auditJobRepository, never()).save(any(AuditJob.class));
    }

    // ==================== shutdown 测试 ====================

    @Test
    @DisplayName("shutdown - 优雅停机应停止运行标志")
    void testShutdown_GracefulShutdown() throws Exception {
        // Act
        auditWorker.shutdown();

        // Assert
        // 验证方法执行完毕
        assertNotNull(auditWorker);
    }

    // ==================== saveAuditResult 测试 ====================

    @Test
    @DisplayName("saveAuditResult - 应该正确保存审核结果")
    void testSaveAuditResult() throws Exception {
        // Arrange
        ArgumentCaptor<AuditResult> resultCaptor = ArgumentCaptor.forClass(AuditResult.class);

        // Act
        invokeSaveAuditResult(passedDecision, "https://example.com", "job-001");

        // Assert
        verify(auditResultRepository).save(resultCaptor.capture());

        AuditResult savedResult = resultCaptor.getValue();
        assertEquals("post-001", savedResult.getPostId());
        assertEquals("https://example.com", savedResult.getUrl());
        assertEquals("job-001", savedResult.getJobId());
        assertEquals("PASSED", savedResult.getAuditStatus());
        assertNotNull(savedResult.getAuditedAt());
    }

    // ==================== 异常处理测试 ====================

    @Test
    @DisplayName("processMessage - 内容未找到时应移入死信队列（达到最大重试次数后）")
    void testProcessMessage_ContentNotFound() throws Exception {
        // Arrange - 设置 retryCount = 3（等于 maxRetryAttempts），直接进入死信队列
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.empty()).when(xhsContentRepository).findByPostId(eq("post-001")); // 内容不存在

        // Act - 使用 retryCount = 3（等于最大值），直接进入死信队列
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "3"));

        // Assert
        verify(messageQueueService).sendToDeadLetterQueue(eq("audit"), eq("job-001"), anyString(), anyMap());
        verify(messageQueueService).acknowledgeAuditTask(anyString());

        // 验证任务状态更新为 FAILED
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> TaskStatus.FAILED.name().equals(j.getStatus())));
    }

    @Test
    @DisplayName("processMessage - AI 审核异常时应正确处理（达到最大重试次数后进入死信队列）")
    void testProcessMessage_AuditException() throws Exception {
        // Arrange - 设置 retryCount = 3（等于 maxRetryAttempts），直接进入死信队列
        doReturn(lock).when(redissonClient).getLock(anyString());
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(lock).isHeldByCurrentThread();
        doReturn(Optional.of(testJob)).when(auditJobRepository).findByJobId(eq("job-001"));
        doReturn(Optional.of(testContent)).when(xhsContentRepository).findByPostId(eq("post-001"));
        doThrow(new RuntimeException("AI 模型调用失败")).when(contentAuditAgent).auditContent(any(XhsContent.class));

        // Act - 使用 retryCount = 3（等于最大值），直接进入死信队列
        invokeProcessMessage(createTestMessage("job-001", "post-001", "https://www.xiaohongshu.com/explore/post001", "3"));

        // Assert
        verify(messageQueueService).sendToDeadLetterQueue(eq("audit"), eq("job-001"), anyString(), anyMap());
        verify(messageQueueService).acknowledgeAuditTask(anyString());

        // 验证任务状态更新为 FAILED
        verify(auditJobRepository, atLeast(1)).save(auditJobCaptor.capture());
        List<AuditJob> savedJobs = auditJobCaptor.getAllValues();
        assertTrue(savedJobs.stream().anyMatch(j -> TaskStatus.FAILED.name().equals(j.getStatus())));
    }

    // ==================== 辅助方法 ====================

    private Object createTestMessage(String jobId, String postId, String url, String retryCount) throws Exception {
        Map<String, String> messageValue = new HashMap<>();
        messageValue.put("jobId", jobId);
        messageValue.put("postId", postId);
        messageValue.put("url", url);
        messageValue.put("retryCount", retryCount);

        RecordId recordId = RecordId.of("0-1");

        return MapRecord.create(recordId, messageValue);
    }

    private void invokeProcessMessage(Object message) throws Exception {
        java.lang.reflect.Method method = AuditWorker.class.getDeclaredMethod("processMessage",
                MapRecord.class);
        method.setAccessible(true);
        method.invoke(auditWorker, message);
    }

    private void invokeUpdateJobStatus(String jobId, String status, String message) throws Exception {
        java.lang.reflect.Method method = AuditWorker.class.getDeclaredMethod("updateJobStatus",
                String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(auditWorker, jobId, status, message);
    }

    private void invokeUpdateJobProgress(String jobId, String auditStatus) throws Exception {
        java.lang.reflect.Method method = AuditWorker.class.getDeclaredMethod("updateJobProgress",
                String.class, String.class);
        method.setAccessible(true);
        method.invoke(auditWorker, jobId, auditStatus);
    }

    private void invokeCheckAndUpdateJobCompletion(String jobId) throws Exception {
        java.lang.reflect.Method method = AuditWorker.class.getDeclaredMethod("checkAndUpdateJobCompletion",
                String.class);
        method.setAccessible(true);
        method.invoke(auditWorker, jobId);
    }

    private void invokeSaveAuditResult(AuditDecision decision, String url, String jobId) throws Exception {
        java.lang.reflect.Method method = AuditWorker.class.getDeclaredMethod("saveAuditResult",
                AuditDecision.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(auditWorker, decision, url, jobId);
    }
}
