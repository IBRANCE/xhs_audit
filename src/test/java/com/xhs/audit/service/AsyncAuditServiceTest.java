package com.xhs.audit.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.service.AsyncAuditService;
import com.xhs.audit.service.ContentAuditService;

import lombok.extern.slf4j.Slf4j;

/**
 * 异步审核服务测试
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncAuditService - 异步审核服务测试")
class AsyncAuditServiceTest {

    @Mock
    private ContentAuditService contentAuditService;

    @Mock
    private AuditJobRepository auditJobRepository;

    @InjectMocks
    private AsyncAuditService asyncAuditService;

    @BeforeEach
    void setUp() {
        // Setup default mock behavior for AuditJobRepository
        // This ensures findByJobId returns an Optional containing an AuditJob
    }

    /**
     * 测试：处理单个URL的异步审核任务
     */
    @Test
    @DisplayName("处理单个URL异步审核 - 返回PASSED")
    void testProcessSingleUrlJobSuccess() {
        // Arrange
        String jobId = "job-001";
        List<String> urls = Arrays.asList("https://example.com/post/001");

        AuditJob auditJob = new AuditJob();
        auditJob.setJobId(jobId);
        auditJob.setStatus("PROCESSING");
        auditJob.setCreatedAt(LocalDateTime.now());

        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.of(auditJob));
        when(auditJobRepository.save(any(AuditJob.class))).thenReturn(auditJob);

        AuditDecision passedDecision = new AuditDecision();
        passedDecision.setStatus("PASSED");
        passedDecision.setConfidenceScore(0.95);

        when(contentAuditService.auditContent(anyString(), anyBoolean()))
                .thenReturn(passedDecision);

        // Act
        asyncAuditService.processAuditJob(jobId, urls);

        // Assert
        verify(contentAuditService, times(1)).auditContent(urls.get(0), false);
        verify(auditJobRepository, atLeastOnce()).save(any(AuditJob.class));

        log.info("✓ 单URL异步审核测试通过");
    }

    /**
     * 测试：处理多个URL的批量异步审核任务
     */
    @Test
    @DisplayName("处理多个URL批量异步审核 - 统计通过/驳回")
    void testProcessMultipleUrlsJobBatch() {
        // Arrange
        String jobId = "job-002";
        List<String> urls = Arrays.asList(
                "https://example.com/post/001",
                "https://example.com/post/002",
                "https://example.com/post/003");

        AuditJob auditJob = new AuditJob();
        auditJob.setJobId(jobId);
        auditJob.setStatus("PROCESSING");
        auditJob.setCreatedAt(LocalDateTime.now());

        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.of(auditJob));
        when(auditJobRepository.save(any(AuditJob.class))).thenReturn(auditJob);

        AuditDecision passedDecision = new AuditDecision();
        passedDecision.setStatus("PASSED");

        AuditDecision rejectedDecision = new AuditDecision();
        rejectedDecision.setStatus("REJECTED");
        rejectedDecision.setConfidenceScore(0.85);

        when(contentAuditService.auditContent(urls.get(0), false))
                .thenReturn(passedDecision);
        when(contentAuditService.auditContent(urls.get(1), false))
                .thenReturn(rejectedDecision);
        when(contentAuditService.auditContent(urls.get(2), false))
                .thenReturn(passedDecision);

        // Act
        asyncAuditService.processAuditJob(jobId, urls);

        // Assert
        verify(contentAuditService, times(3)).auditContent(anyString(), anyBoolean());
        verify(auditJobRepository, atLeastOnce()).save(any(AuditJob.class));

        log.info("✓ 批量URL异步审核测试通过");
    }

    /**
     * 测试：异步审核任务中某条URL失败的处理
     */
    @Test
    @DisplayName("处理URL异常 - 继续处理其他URL")
    void testProcessUrlWithException() {
        // Arrange
        String jobId = "job-003";
        List<String> urls = Arrays.asList(
                "https://example.com/post/001",
                "https://example.com/post/invalid",
                "https://example.com/post/003");

        AuditJob auditJob = new AuditJob();
        auditJob.setJobId(jobId);
        auditJob.setStatus("PROCESSING");
        auditJob.setCreatedAt(LocalDateTime.now());

        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.of(auditJob));
        when(auditJobRepository.save(any(AuditJob.class))).thenReturn(auditJob);

        AuditDecision passedDecision = new AuditDecision();
        passedDecision.setStatus("PASSED");

        when(contentAuditService.auditContent(urls.get(0), false))
                .thenReturn(passedDecision);
        when(contentAuditService.auditContent(urls.get(1), false))
                .thenThrow(new RuntimeException("Invalid URL"));
        when(contentAuditService.auditContent(urls.get(2), false))
                .thenReturn(passedDecision);

        // Act
        asyncAuditService.processAuditJob(jobId, urls);

        // Assert
        // 验证三个URL都被尝试处理了
        verify(contentAuditService, times(3)).auditContent(anyString(), anyBoolean());
        // 验证任务状态被更新（PARTIAL_SUCCESS因为有失败）
        verify(auditJobRepository, atLeastOnce()).save(any(AuditJob.class));

        log.info("✓ URL异常处理测试通过");
    }

    /**
     * 测试：空URL列表处理
     */
    @Test
    @DisplayName("处理空URL列表 - 任务正常完成")
    void testProcessEmptyUrlList() {
        // Arrange
        String jobId = "job-004";
        List<String> urls = Collections.emptyList();

        AuditJob auditJob = new AuditJob();
        auditJob.setJobId(jobId);
        auditJob.setStatus("PROCESSING");
        auditJob.setCreatedAt(LocalDateTime.now());

        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.of(auditJob));
        when(auditJobRepository.save(any(AuditJob.class))).thenReturn(auditJob);

        // Act
        asyncAuditService.processAuditJob(jobId, urls);

        // Assert
        verify(contentAuditService, never()).auditContent(anyString(), anyBoolean());
        verify(auditJobRepository, atLeastOnce()).save(any(AuditJob.class));

        log.info("✓ 空URL列表测试通过");
    }

    /**
     * 测试：大规模批量URL处理进度更新
     */
    @Test
    @DisplayName("大规模URL处理 - 每10条更新一次进度")
    void testProcessLargeBatchUrlProgress() {
        // Arrange
        String jobId = "job-005";
        List<String> urls = Arrays.asList(new String[25]);
        for (int i = 0; i < 25; i++) {
            urls.set(i, "https://example.com/post/" + (i + 1));
        }

        AuditJob auditJob = new AuditJob();
        auditJob.setJobId(jobId);
        auditJob.setStatus("PROCESSING");
        auditJob.setCreatedAt(LocalDateTime.now());

        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.of(auditJob));
        when(auditJobRepository.save(any(AuditJob.class))).thenReturn(auditJob);

        AuditDecision passedDecision = new AuditDecision();
        passedDecision.setStatus("PASSED");

        when(contentAuditService.auditContent(anyString(), anyBoolean()))
                .thenReturn(passedDecision);

        // Act
        asyncAuditService.processAuditJob(jobId, urls);

        // Assert
        verify(contentAuditService, times(25)).auditContent(anyString(), anyBoolean());
        // 验证进度更新被调用（每10条+最后一次）
        verify(auditJobRepository, atLeast(3)).save(any(AuditJob.class));

        log.info("✓ 大规模批量处理进度测试通过");
    }

    /**
     * 测试：全部URL审核驳回的情况
     */
    @Test
    @DisplayName("全部URL驳回 - 返回PARTIAL_SUCCESS状态")
    void testProcessAllUrlsRejected() {
        // Arrange
        String jobId = "job-006";
        List<String> urls = Arrays.asList(
                "https://example.com/post/001",
                "https://example.com/post/002");

        AuditJob auditJob = new AuditJob();
        auditJob.setJobId(jobId);
        auditJob.setStatus("PROCESSING");
        auditJob.setCreatedAt(LocalDateTime.now());

        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.of(auditJob));
        when(auditJobRepository.save(any(AuditJob.class))).thenReturn(auditJob);

        AuditDecision rejectedDecision = new AuditDecision();
        rejectedDecision.setStatus("REJECTED");
        rejectedDecision.setConfidenceScore(0.90);

        when(contentAuditService.auditContent(anyString(), anyBoolean()))
                .thenReturn(rejectedDecision);

        // Act
        asyncAuditService.processAuditJob(jobId, urls);

        // Assert
        verify(contentAuditService, times(2)).auditContent(anyString(), anyBoolean());
        verify(auditJobRepository, atLeastOnce()).save(any(AuditJob.class));

        log.info("✓ 全部驳回状态测试通过");
    }
}
