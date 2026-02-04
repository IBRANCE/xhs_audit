package com.xhs.audit.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.exception.ResourceNotFoundException;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.CrawlTaskMessage;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.repository.AuditJobRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * 异步审核控制器
 * v4.0: 基于 Redis Stream 的异步审核 API
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "AsyncAudit", description = "异步审核API (v4.0)")
@Slf4j
public class AsyncAuditController {

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private AuditJobRepository auditJobRepository;

    /**
     * 提交异步审核任务
     */
    @PostMapping("/async")
    @Operation(summary = "异步审核", description = "提交URL到消息队列异步处理，返回jobId供查询")
    public ResponseEntity<Map<String, Object>> submitAsyncAudit(
            @RequestBody Map<String, Object> request) {

        String url = (String) request.get("url");
        if (url == null || url.isBlank()) {
            throw new BusinessException("INVALID_PARAM", "URL不能为空");
        }

        String source = (String) request.getOrDefault("source", "API");
        boolean forceRefresh = Boolean.parseBoolean(
                String.valueOf(request.getOrDefault("forceRefresh", "false")));

        // 生成 jobId
        String jobId = UUID.randomUUID().toString();

        // 设置 MDC 追踪上下文
        MDC.put("jobId", jobId);
        MDC.put("url", url);

        try {
            // 创建任务记录
            AuditJob job = AuditJob.builder()
                    .jobId(jobId)
                    .url(url)
                    .status("PENDING")
                    .totalLinks(1)
                    .completedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .message("任务已提交，等待爬取")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            auditJobRepository.save(job);
            log.info("[AsyncAudit] 📝 任务记录已创建");

            // 发送到消息队列
            CrawlTaskMessage message = new CrawlTaskMessage(jobId, url, source, forceRefresh);
            String recordId = messageQueueService.sendCrawlTask(message);

            if (recordId == null) {
                log.error("[AsyncAudit] ❌ 任务提交失败，可能是重复提交");
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "任务提交失败，可能是重复提交"
                ));
            }

            log.info("[AsyncAudit] ✅ 任务已提交到队列: recordId={}", recordId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("jobId", jobId);
            response.put("message", "任务已提交，请使用 /api/audit/result/{jobId} 查询结果");
            response.put("checkUrl", "/api/audit/result/" + jobId);

            return ResponseEntity.accepted().body(response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * 查询审核结果
     */
    @GetMapping("/result/{jobId}")
    @Operation(summary = "查询审核结果", description = "根据jobId查询异步审核结果")
    public ResponseEntity<Map<String, Object>> getAuditResult(@PathVariable String jobId) {
        AuditJob job = auditJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JOB_NOT_FOUND", "任务不存在: " + jobId));

        Map<String, Object> result = new HashMap<>();
        result.put("jobId", job.getJobId());
        result.put("url", job.getUrl() != null ? job.getUrl() : "");
        result.put("status", job.getStatus());
        result.put("progress", calculateProgress(job));
        result.put("message", job.getMessage() != null ? job.getMessage() : "");
        result.put("totalLinks", job.getTotalLinks());
        result.put("completedCount", job.getCompletedCount());
        result.put("successCount", job.getSuccessCount());
        result.put("failedCount", job.getFailedCount());
        result.put("createdAt", job.getCreatedAt());
        result.put("updatedAt", job.getUpdatedAt());
        result.put("completedAt", job.getCompletedAt());

        return ResponseEntity.ok(result);
    }

    /**
     * v4.0: 查询死信队列
     */
    @GetMapping("/dead-letter")
    @Operation(summary = "查询死信队列", description = "查询处理失败的任务列表")
    public ResponseEntity<Map<String, Object>> getDeadLetterQueue(
            @RequestParam(defaultValue = "50") int limit) {

        List<MapRecord<String, Object, Object>> records = messageQueueService.queryDeadLetterQueue(limit);

        List<Map<String, Object>> tasks = records.stream()
                .map(record -> {
                    Map<String, Object> task = new HashMap<>();
                    task.put("messageId", record.getId().getValue());
                    task.put("stream", record.getValue().getOrDefault("stream", ""));
                    task.put("jobId", record.getValue().getOrDefault("jobId", ""));
                    task.put("reason", record.getValue().getOrDefault("reason", ""));
                    task.put("timestamp", record.getValue().getOrDefault("timestamp", ""));
                    return task;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("total", tasks.size());
        response.put("tasks", tasks);

        return ResponseEntity.ok(response);
    }

    /**
     * v4.0: 批量提交任务
     */
    @PostMapping("/async/batch")
    @Operation(summary = "批量异步审核", description = "批量提交URL到消息队列")
    public ResponseEntity<Map<String, Object>> submitBatchAsyncAudit(
            @RequestBody Map<String, Object> request) {

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) request.get("urls");

        if (urls == null || urls.isEmpty()) {
            throw new BusinessException("INVALID_PARAM", "URLs不能为空");
        }

        String source = (String) request.getOrDefault("source", "BATCH");
        boolean forceRefresh = Boolean.parseBoolean(
                String.valueOf(request.getOrDefault("forceRefresh", "false")));

        String batchJobId = UUID.randomUUID().toString();
        int successCount = 0;
        int failedCount = 0;
        List<String> failedUrls = new ArrayList<>();
        int index = 0;

        for (String url : urls) {
            try {
                String jobId = batchJobId + "-" + index;

                AuditJob job = AuditJob.builder()
                        .jobId(jobId)
                        .url(url)
                        .status("PENDING")
                        .totalLinks(1)
                        .completedCount(0)
                        .successCount(0)
                        .failedCount(0)
                        .message("批量任务已提交")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                auditJobRepository.save(job);

                CrawlTaskMessage message = new CrawlTaskMessage(jobId, url, source, forceRefresh);
                String recordId = messageQueueService.sendCrawlTask(message);

                if (recordId != null) {
                    successCount++;
                } else {
                    failedCount++;
                    failedUrls.add(url);
                    log.warn("[AsyncAudit] 任务提交失败（可能重复）: url={}", url);
                }
            } catch (Exception e) {
                failedCount++;
                failedUrls.add(url);
                log.error("[AsyncAudit] 批量任务提交失败: url={}", url, e);
            } finally {
                index++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("batchJobId", batchJobId);
        response.put("totalSubmitted", successCount);
        response.put("totalRequested", urls.size());
        response.put("totalFailed", failedCount);
        if (!failedUrls.isEmpty()) {
            response.put("failedUrls", failedUrls);
        }
        response.put("message", String.format("批量任务已提交：成功 %d，失败 %d", successCount, failedCount));

        return ResponseEntity.accepted().body(response);
    }

    /**
     * v4.0: 获取队列统计信息
     */
    @GetMapping("/queue/stats")
    @Operation(summary = "队列统计", description = "获取 Redis Stream 队列统计信息")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        Map<String, Object> stats = messageQueueService.getQueueStats();
        return ResponseEntity.ok(stats);
    }

    private int calculateProgress(AuditJob job) {
        if (job.getTotalLinks() == null || job.getTotalLinks() == 0)
            return 0;
        return (job.getCompletedCount() * 100) / job.getTotalLinks();
    }
}
