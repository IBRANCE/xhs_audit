package com.xhs.audit.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xhs.audit.exception.ResourceNotFoundException;
import com.xhs.audit.model.dto.ApiResponse;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.dto.AuditRequest;
import com.xhs.audit.model.dto.BatchAuditRequest;
import com.xhs.audit.model.dto.JobStatusResponse;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;
import com.xhs.audit.service.ContentAuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * 审核Controller
 * 提供REST API端点
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@Validated
@Tag(name = "审核管理", description = "小红书内容审核相关接口")
public class AuditController {

    @Autowired
    private ContentAuditService contentAuditService;

    @Autowired
    private AuditResultRepository auditResultRepository;

    @Autowired
    private AuditJobRepository auditJobRepository;

    /**
     * POST /api/v1/audit/content - 单条内容审核
     */
        @Operation(summary = "单条内容审核", description = "对单个小红书链接进行内容审核")
        @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", 
                description = "审核成功",
                content = @Content(schema = @Schema(implementation = AuditDecision.class))
            )
        })
    @PostMapping("/content")
    public ResponseEntity<com.xhs.audit.model.dto.ApiResponse<AuditDecision>> auditContent(
            @Valid @RequestBody AuditRequest request) {

        log.info("收到审核请求: url={}", request.getUrl());

        AuditDecision decision = contentAuditService.auditContent(
                request.getUrl(),
                request.getForceRefresh());

        return ResponseEntity.ok(com.xhs.audit.model.dto.ApiResponse.success(decision));
    }

    /**
     * POST /api/v1/audit/batch - 批量审核（同步，测试用）
     */
        @Operation(summary = "批量审核", description = "同步批量审核多个小红书链接（测试用）")
    @PostMapping("/batch")
    public ResponseEntity<com.xhs.audit.model.dto.ApiResponse<BatchAuditResponse>> batchAudit(
            @Valid @RequestBody BatchAuditRequest request) {

        log.info("收到批量审核请求: count={}", request.getLinks().size());

        List<AuditDecision> results = new ArrayList<>();
        int passedCount = 0;
        int rejectedCount = 0;

        for (String url : request.getLinks()) {
            try {
                AuditDecision decision = contentAuditService.auditContent(url, false);
                results.add(decision);

                if ("PASSED".equals(decision.getStatus())) {
                    passedCount++;
                } else if ("REJECTED".equals(decision.getStatus())) {
                    rejectedCount++;
                }
            } catch (Exception e) {
                log.error("批量审核失败: url={}", url, e);
                // 创建失败决策
                AuditDecision failedDecision = AuditDecision.uncertain("unknown", "审核失败: " + e.getMessage());
                failedDecision.setUrl(url);
                results.add(failedDecision);
            }
        }

        BatchAuditResponse response = BatchAuditResponse.builder()
                .totalCount(request.getLinks().size())
                .passedCount(passedCount)
                .rejectedCount(rejectedCount)
                .results(results)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/audit/job/{jobId} - 查询任务进度
     */
        @Operation(summary = "查询任务进度", description = "根据任务ID查询审核任务的进度和状态")
        @Parameter(name = "jobId", description = "任务ID", required = true)
    @GetMapping("/job/{jobId}")
    public ResponseEntity<ApiResponse<JobStatusResponse>> getJobStatus(
            @PathVariable String jobId) {

        log.info("查询任务进度: jobId={}", jobId);

        AuditJob job = auditJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_JOB_NOT_FOUND",
                        "任务不存在: " + jobId));

        // 计算进度
        int progressPercent = job.getTotalLinks() > 0
                ? (job.getCompletedCount() * 100 / job.getTotalLinks())
                : 0;

        JobStatusResponse response = JobStatusResponse.builder()
                .jobId(job.getJobId())
                .totalLinks(job.getTotalLinks())
                .completedCount(job.getCompletedCount())
                .passedCount(job.getSuccessCount())
                .rejectedCount(job.getFailedCount())
                .status(job.getStatus())
                .progressPercent(progressPercent)
                .createdTime(job.getCreatedAt())
                .estimatedCompletionTime(null) // TODO: 计算预估时间
                .errorCount(job.getFailedCount())
                .errorSummary(new ArrayList<>())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/audit/result/{postId} - 获取单条审核结果
     */
        @Operation(summary = "获取审核结果", description = "根据帖子ID获取单条内容的审核结果")
        @Parameter(name = "postId", description = "帖子ID", required = true)
    @GetMapping("/result/{postId}")
    public ResponseEntity<ApiResponse<AuditDecision>> getAuditResult(
            @PathVariable String postId) {

        log.info("查询审核结果: postId={}", postId);

        AuditResult result = auditResultRepository.findByPostId(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_RESULT_NOT_FOUND",
                        "审核结果不存在: " + postId));

        AuditDecision decision = convertToDecision(result);

        return ResponseEntity.ok(ApiResponse.success(decision));
    }

    /**
     * 转换AuditResult为AuditDecision
     */
    private AuditDecision convertToDecision(AuditResult result) {
        return AuditDecision.builder()
                .postId(result.getPostId())
                .url(result.getUrl())
                .status(result.getAuditStatus())
                .reasons(new ArrayList<>()) // TODO: 解析JSON
                .confidenceScore(result.getConfidenceScore() != null ? result.getConfidenceScore().doubleValue() : 0.0)
                .modelName(result.getModelName())
                .auditedTime(result.getAuditedAt())
                .build();
    }

    /**
     * 批量审核响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchAuditResponse {
        private Integer totalCount;
        private Integer passedCount;
        private Integer rejectedCount;
        private List<AuditDecision> results;
    }
}
