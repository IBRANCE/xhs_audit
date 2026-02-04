package com.xhs.audit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.CrawlTaskMessage;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.repository.AuditJobRepository;

/**
 * AsyncAuditController 测试
 * v4.0: 验证异步审核 API 控制器
 *
 * @author XHS Audit System
 * @since 2026-02-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncAuditController - 异步审核控制器测试")
class AsyncAuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageQueueService messageQueueService;

    @Mock
    private AuditJobRepository auditJobRepository;

    @InjectMocks
    private AsyncAuditController asyncAuditController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(asyncAuditController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("submitAsyncAudit - 成功提交审核任务")
    void testSubmitAsyncAuditSuccess() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("url", "https://example.com/post/001");
        request.put("source", "API");
        request.put("forceRefresh", false);

        when(messageQueueService.sendCrawlTask(any(CrawlTaskMessage.class)))
                .thenReturn("0-0");

        // Act & Assert
        mockMvc.perform(post("/api/audit/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.message").value("任务已提交，请使用 /api/audit/result/{jobId} 查询结果"));

        verify(auditJobRepository).save(any(AuditJob.class));
        verify(messageQueueService).sendCrawlTask(any(CrawlTaskMessage.class));
    }

    @Test
    @DisplayName("submitAsyncAudit - URL为空时返回错误")
    void testSubmitAsyncAuditEmptyUrl() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("url", "");
        request.put("source", "API");

        // Act & Assert
        mockMvc.perform(post("/api/audit/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    @DisplayName("submitAsyncAudit - 重复提交返回错误")
    void testSubmitAsyncAuditDuplicate() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("url", "https://example.com/post/001");

        when(messageQueueService.sendCrawlTask(any(CrawlTaskMessage.class)))
                .thenReturn(null);

        // Act & Assert
        mockMvc.perform(post("/api/audit/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMIT_FAILED"));
    }

    @Test
    @DisplayName("getAuditResult - 获取审核结果")
    void testGetAuditResult() throws Exception {
        // Arrange
        String jobId = "job-001";
        AuditJob job = new AuditJob();
        job.setJobId(jobId);
        job.setUrl("https://example.com/post/001");
        job.setStatus("COMPLETED");
        job.setTotalLinks(1);
        job.setCompletedCount(1);
        job.setSuccessCount(1);
        job.setFailedCount(0);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.of(job));

        // Act & Assert
        mockMvc.perform(get("/api/audit/result/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100));
    }

    @Test
    @DisplayName("getAuditResult - 任务不存在")
    void testGetAuditResultNotFound() throws Exception {
        // Arrange
        String jobId = "nonexistent-job";
        when(auditJobRepository.findByJobId(jobId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/audit/result/{jobId}", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("submitBatchAsyncAudit - 批量提交成功")
    void testSubmitBatchAsyncAudit() throws Exception {
        // Arrange
        @SuppressWarnings("unchecked")
        Map<String, Object> request = new HashMap<>();
        request.put("urls", List.of(
                "https://example.com/post/001",
                "https://example.com/post/002",
                "https://example.com/post/003"));
        request.put("source", "BATCH");

        when(messageQueueService.sendCrawlTask(any(CrawlTaskMessage.class)))
                .thenReturn("0-0");

        // Act & Assert
        mockMvc.perform(post("/api/audit/async/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.batchJobId").exists())
                .andExpect(jsonPath("$.totalSubmitted").value(3))
                .andExpect(jsonPath("$.totalRequested").value(3))
                .andExpect(jsonPath("$.totalFailed").value(0));

        verify(auditJobRepository, times(3)).save(any(AuditJob.class));
        verify(messageQueueService, times(3)).sendCrawlTask(any(CrawlTaskMessage.class));
    }

    @Test
    @DisplayName("submitBatchAsyncAudit - 部分失败")
    void testSubmitBatchAsyncAuditPartialFailure() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("urls", List.of(
                "https://example.com/post/001",
                "https://example.com/post/002",
                "https://example.com/post/003"));

        // 第二个任务返回 null（重复提交）
        when(messageQueueService.sendCrawlTask(any(CrawlTaskMessage.class)))
                .thenReturn("0-0")
                .thenReturn(null)
                .thenReturn("0-2");

        // Act & Assert
        mockMvc.perform(post("/api/audit/async/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.totalSubmitted").value(2))
                .andExpect(jsonPath("$.totalFailed").value(1))
                .andExpect(jsonPath("$.failedUrls").isArray())
                .andExpect(jsonPath("$.failedUrls[0]").value("https://example.com/post/002"));
    }

    @Test
    @DisplayName("submitBatchAsyncAudit - 空URL列表")
    void testSubmitBatchAsyncAuditEmptyUrls() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("urls", List.of());

        // Act & Assert
        mockMvc.perform(post("/api/audit/async/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    @DisplayName("getQueueStats - 获取队列统计")
    void testGetQueueStats() throws Exception {
        // Arrange
        Map<String, Object> stats = new HashMap<>();
        stats.put("crawlQueueSize", 10L);
        stats.put("auditQueueSize", 5L);
        stats.put("deadLetterQueueSize", 2L);

        when(messageQueueService.getQueueStats()).thenReturn(stats);

        // Act & Assert
        mockMvc.perform(get("/api/audit/queue/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crawlQueueSize").value(10))
                .andExpect(jsonPath("$.auditQueueSize").value(5))
                .andExpect(jsonPath("$.deadLetterQueueSize").value(2));
    }

    @Test
    @DisplayName("计算进度百分比")
    void testCalculateProgress() {
        // Arrange
        AuditJob job = new AuditJob();
        job.setTotalLinks(10);
        job.setCompletedCount(5);

        // Act
        int progress = (job.getCompletedCount() * 100) / job.getTotalLinks();

        // Assert
        assertEquals(50, progress);
    }

    @Test
    @DisplayName("计算进度 - 零链接")
    void testCalculateProgressZeroLinks() {
        // Arrange
        AuditJob job = new AuditJob();
        job.setTotalLinks(0);
        job.setCompletedCount(0);

        // Act
        int progress = job.getTotalLinks() == 0 ? 0 : (job.getCompletedCount() * 100) / job.getTotalLinks();

        // Assert
        assertEquals(0, progress);
    }
}
