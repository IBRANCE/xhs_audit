package com.xhs.audit.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xhs.audit.exception.ResourceNotFoundException;
import com.xhs.audit.model.dto.ApiResponse;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.dto.AuditRequest;
import com.xhs.audit.model.dto.AuditResultItem;
import com.xhs.audit.model.dto.BatchAuditRequest;
import com.xhs.audit.model.dto.JobStatusResponse;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;
import com.xhs.audit.service.ContentAuditService;
import com.xhs.audit.util.AuditResultConverter;

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

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    /**
     * POST /api/v1/audit/content - 单条内容审核
     */
    @Operation(summary = "单条内容审核", description = "对单个小红书链接进行内容审核")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "审核成功", content = @Content(schema = @Schema(implementation = AuditDecision.class)))
    })
    @PostMapping("/content")
    public ResponseEntity<com.xhs.audit.model.dto.ApiResponse<AuditDecision>> auditContent(
            @Valid @RequestBody AuditRequest request) {

        log.info("收到审核请求: url={}", request.getUrl());

        AuditDecision decision = contentAuditService.auditContent(
                request.getUrl(),
                request.getForceRefresh(),
                null); // 单条审核没有 jobId

        return ResponseEntity.ok(com.xhs.audit.model.dto.ApiResponse.success(decision));
    }

    /**
     * POST /api/v1/audit/batch - 批量审核（并发执行）
     */
    @Operation(summary = "批量审核", description = "并发批量审核多个小红书链接，支持最多4个并发爬虫")
    @PostMapping("/batch")
    public ResponseEntity<com.xhs.audit.model.dto.ApiResponse<BatchAuditResponse>> batchAudit(
            @Valid @RequestBody BatchAuditRequest request) {

        log.info("收到批量审核请求: count={}", request.getLinks().size());

        // 使用 CompletableFuture 实现并发审核
        List<CompletableFuture<AuditDecision>> futures = request.getLinks().stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    try {
                        log.debug("开始审核: url={}", url);
                        return contentAuditService.auditContent(url, false, null);
                    } catch (Exception e) {
                        log.error("批量审核失败: url={}", url, e);
                        // 创建失败决策
                        AuditDecision failedDecision = AuditDecision.uncertain("unknown", "审核失败: " + e.getMessage());
                        failedDecision.setUrl(url);
                        return failedDecision;
                    }
                }, taskExecutor))
                .collect(Collectors.toList());

        // 等待所有任务完成
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        List<AuditDecision> results = allOf.thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())).join();

        // 统计结果
        int passedCount = 0;
        int rejectedCount = 0;
        for (AuditDecision decision : results) {
            if ("PASSED".equals(decision.getStatus())) {
                passedCount++;
            } else if ("REJECTED".equals(decision.getStatus())) {
                rejectedCount++;
            }
        }

        BatchAuditResponse response = BatchAuditResponse.builder()
                .totalCount(request.getLinks().size())
                .passedCount(passedCount)
                .rejectedCount(rejectedCount)
                .results(results)
                .build();

        log.info("批量审核完成: total={}, passed={}, rejected={}",
                request.getLinks().size(), passedCount, rejectedCount);

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
     * GET /api/v1/audit/results - 查询审核结果列表（分页、筛选）
     */
    @Operation(summary = "获取审核结果列表", description = "分页获取审核结果，支持按postId、jobId、状态、时间筛选")
    @GetMapping("/results")
    public ResponseEntity<ApiResponse<Page<AuditResultItem>>> getAuditResults(
            @Parameter(description = "页码", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "帖子ID精确匹配") @RequestParam(required = false) String postId,
            @Parameter(description = "任务ID精确匹配") @RequestParam(required = false) String jobId,
            @Parameter(description = "审核状态筛选: PASSED/REJECTED/UNCERTAIN") @RequestParam(required = false) String status,
            @Parameter(description = "开始时间 (yyyy-MM-dd)") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束时间 (yyyy-MM-dd)") @RequestParam(required = false) String endDate) {

        log.info("收到审核结果列表请求: page={}, size={}, postId={}, jobId={}, status={}, startDate={}, endDate={}",
                page, size, postId, jobId, status, startDate, endDate);

        // 解析时间参数
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;
        if (startDate != null && !startDate.isEmpty()) {
            startDateTime = LocalDateTime.parse(startDate + "T00:00:00");
        }
        if (endDate != null && !endDate.isEmpty()) {
            endDateTime = LocalDateTime.parse(endDate + "T23:59:59");
        }

        // 构建分页参数
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "auditedAt"));

        // 执行查询
        Page<AuditResultItem> results = contentAuditService.searchAuditResults(
                postId, jobId, status, startDateTime, endDateTime, pageable);

        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * GET /api/v1/audit/results/{postId}/detail - 获取审核详情
     */
    @Operation(summary = "获取审核详情", description = "根据帖子ID获取完整的审核详情（包括xhs_content内容）")
    @GetMapping("/results/{postId}/detail")
    public ResponseEntity<ApiResponse<com.xhs.audit.model.dto.AuditDetailResponse>> getAuditDetail(
            @Parameter(description = "帖子ID", required = true) @PathVariable String postId) {

        log.info("收到审核详情请求: postId={}", postId);

        com.xhs.audit.model.dto.AuditDetailResponse detail = contentAuditService.getAuditDetailByPostId(postId);

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * 转换AuditResult为AuditDecision
     */
    private AuditDecision convertToDecision(AuditResult result) {
        return AuditResultConverter.toDecision(result);
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
