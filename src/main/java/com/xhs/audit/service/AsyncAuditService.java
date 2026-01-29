package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.xhs.audit.model.entity.XhsContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.repository.AuditJobRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 异步审核服务
 * 处理批量审核任务的流水线并行执行
 *
 * 流水线模式:
 * - 爬取阶段: 单线程 (crawlExecutor) - 避免CDP冲突
 * - 审核阶段: 多线程 (auditTaskExecutor) - 并行处理
 * - 流水线: 爬取完成后立即触发审核，无需等待所有爬取完成
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Service
public class AsyncAuditService {

    @Autowired
    private ContentAuditService contentAuditService;

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired
    @Qualifier("crawlExecutor")
    private Executor crawlExecutor;

    @Autowired
    @Qualifier("auditTaskExecutor")
    private Executor auditTaskExecutor;

    /**
     * 异步流水线处理审核任务
     * 爬取阶段单线程执行，审核阶段多线程并行
     *
     * @param jobId 任务ID
     * @param urls  待审核的URL列表
     */
    @Async("taskExecutor")
    public void processAuditJob(String jobId, List<String> urls) {
        log.info("========================================");
        log.info("[流水线审核] 开始后台处理: jobId={}, totalLinks={}", jobId, urls.size());
        log.info("[流水线审核] 爬取线程=crawl-*, 审核线程=audit-async-*");
        log.info("========================================");

        try {
            // 更新任务状态为PROCESSING
            log.info("[流水线审核] 更新任务状态: jobId={}, PENDING -> PROCESSING", jobId);
            updateJobStatus(jobId, "PROCESSING");

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            AtomicInteger completedCount = new AtomicInteger(0);

            // 创建流水线: supplyAsync(crawlExecutor) → thenApplyAsync(auditExecutor)
            List<CompletableFuture<AuditDecision>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    log.info("[流水线-爬取] 开始: jobId={}, url={}, thread={}",
                            jobId, url, Thread.currentThread().getName());
                    try {
                        // 阶段1: 爬取 (单线程)
                        XhsContent content = contentAuditService.crawlContent(url, false);
                        log.info("[流水线-爬取] 完成: jobId={}, postId={}, thread={}",
                                jobId, content.getPostId(), Thread.currentThread().getName());
                        return content;
                    } catch (Exception e) {
                        log.error("[流水线-爬取] 失败: jobId={}, url={}, error={}",
                                jobId, url, e.getMessage());
                        throw new RuntimeException("爬取失败: " + e.getMessage(), e);
                    }
                }, crawlExecutor)
                .thenApplyAsync(content -> {
                    log.info("[流水线-审核] 开始: jobId={}, postId={}, thread={}",
                            jobId, content.getPostId(), Thread.currentThread().getName());
                    try {
                        // 阶段2: 审核 (多线程)
                        AuditDecision decision = contentAuditService.auditContent(content, jobId);
                        log.info("[流水线-审核] 完成: jobId={}, postId={}, status={}, thread={}",
                                jobId, content.getPostId(), decision.getStatus(),
                                Thread.currentThread().getName());
                        return decision;
                    } catch (Exception e) {
                        log.error("[流水线-审核] 失败: jobId={}, postId={}, error={}",
                                jobId, content.getPostId(), e.getMessage());
                        throw new RuntimeException("审核失败: " + e.getMessage(), e);
                    }
                }, auditTaskExecutor))
                .collect(Collectors.toList());

            // 收集结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    AuditDecision decision = futures.get(i).join();
                    int completed = completedCount.incrementAndGet();

                    if ("PASSED".equals(decision.getStatus())) {
                        successCount.incrementAndGet();
                        log.debug("[流水线] 审核通过: jobId={}, postId={}", jobId, decision.getPostId());
                    } else if ("REJECTED".equals(decision.getStatus())) {
                        failedCount.incrementAndGet();
                        log.debug("[流水线] 审核驳回: jobId={}, postId={}, reasons={}",
                                jobId, decision.getPostId(), decision.getReasons());
                    } else {
                        // 其他状态也视为失败
                        failedCount.incrementAndGet();
                        log.warn("[流水线] 审核结果异常: jobId={}, postId={}, status={}",
                                jobId, decision.getPostId(), decision.getStatus());
                    }

                    // 每处理完一条就更新进度
                    updateJobProgress(jobId, completed, successCount.get(), failedCount.get());
                    log.info("[流水线] 进度更新: jobId={}, 已完成={}/{}, 成功={}, 失败={}",
                            jobId, completed, urls.size(), successCount.get(), failedCount.get());

                } catch (Exception e) {
                    completedCount.incrementAndGet();
                    failedCount.incrementAndGet();
                    log.error("[流水线] 单条处理失败: jobId={}, index={}, error={}",
                            jobId, i, e.getMessage());
                    updateJobProgress(jobId, completedCount.get(), successCount.get(), failedCount.get());
                }
            }

            // 更新最终状态
            String finalStatus = (failedCount.get() == 0) ? "COMPLETED" : "PARTIAL_SUCCESS";
            log.info("[流水线审核] 批处理完成: jobId={}, status={}, 总计={}, 成功={}, 失败={}",
                    jobId, finalStatus, urls.size(), successCount.get(), failedCount.get());
            updateJobStatus(jobId, finalStatus);

            log.info("========================================");
            log.info("[流水线审核完成] jobId={}, status={}", jobId, finalStatus);
            log.info("========================================");

        } catch (Exception e) {
            log.error("审核任务失败: jobId={}", jobId, e);
            updateJobStatus(jobId, "FAILED");
        }
    }

    /**
     * 更新任务状态
     */
    private void updateJobStatus(String jobId, String status) {
        auditJobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setUpdatedAt(LocalDateTime.now());

            if ("COMPLETED".equals(status) || "PARTIAL_SUCCESS".equals(status) || "FAILED".equals(status)) {
                job.setCompletedAt(LocalDateTime.now());
            }

            auditJobRepository.save(job);
        });
    }

    /**
     * 更新任务进度
     */
    private void updateJobProgress(String jobId, int completedCount, int successCount, int failedCount) {
        auditJobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setCompletedCount(completedCount);
            job.setSuccessCount(successCount);
            job.setFailedCount(failedCount);
            job.setUpdatedAt(LocalDateTime.now());
            auditJobRepository.save(job);
        });
    }
}
