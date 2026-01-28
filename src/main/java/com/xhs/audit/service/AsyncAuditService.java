package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.repository.AuditJobRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 异步审核服务
 * 处理批量审核任务的并行执行
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
    @Qualifier("auditTaskExecutor")
    private Executor auditTaskExecutor;

    /**
     * 异步并行处理审核任务
     * 使用 CompletableFuture 实现并行爬取，最多3个浏览器实例自动复用
     *
     * @param jobId 任务ID
     * @param urls  待审核的URL列表
     */
    @Async("taskExecutor")
    public void processAuditJob(String jobId, List<String> urls) {
        log.info("========================================");
        log.info("[并行审核] 开始后台处理: jobId={}, totalLinks={}", jobId, urls.size());
        log.info("========================================");

        try {
            // 更新任务状态为PROCESSING
            log.info("[并行审核] 更新任务状态: jobId={}, PENDING -> PROCESSING", jobId);
            updateJobStatus(jobId, "PROCESSING");

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            AtomicInteger completedCount = new AtomicInteger(0);

            // 使用 CompletableFuture 并行处理
            List<CompletableFuture<AuditDecision>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    log.info("[并行审核] 开始处理链接: jobId={}, url={}", jobId, url);
                    try {
                        AuditDecision decision = contentAuditService.auditContent(url, false, jobId);
                        log.info("[并行审核] 完成链接处理: jobId={}, url={}, status={}",
                                jobId, url, decision.getStatus());
                        return decision;
                    } catch (Exception e) {
                        log.error("[并行审核] 链接处理异常: jobId={}, url={}, error={}",
                                jobId, url, e.getMessage());
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
                        log.debug("[并行审核] 审核通过: jobId={}, postId={}", jobId, decision.getPostId());
                    } else if ("REJECTED".equals(decision.getStatus())) {
                        failedCount.incrementAndGet();
                        log.debug("[并行审核] 审核驳回: jobId={}, postId={}, reasons={}",
                                jobId, decision.getPostId(), decision.getReasons());
                    } else {
                        // 其他状态（如需要重新爬取）也视为失败
                        failedCount.incrementAndGet();
                        log.warn("[并行审核] 审核结果异常: jobId={}, postId={}, status={}",
                                jobId, decision.getPostId(), decision.getStatus());
                    }

                    // 每处理完一条就更新进度（并行场景下更准确）
                    updateJobProgress(jobId, completed, successCount.get(), failedCount.get());
                    log.info("[并行审核] 更新进度: jobId={}, 已完成={}/{}, 成功={}, 失败={}",
                            jobId, completed, urls.size(), successCount.get(), failedCount.get());

                } catch (Exception e) {
                    completedCount.incrementAndGet();
                    failedCount.incrementAndGet();
                    log.error("[并行审核] 单条链接审核失败: jobId={}, index={}, error={}",
                            jobId, i, e.getMessage());
                    updateJobProgress(jobId, completedCount.get(), successCount.get(), failedCount.get());
                }
            }

            // 更新最终状态
            String finalStatus = (failedCount.get() == 0) ? "COMPLETED" : "PARTIAL_SUCCESS";
            log.info("[并行审核] 批处理完成: jobId={}, status={}, 总计={}, 成功={}, 失败={}",
                    jobId, finalStatus, urls.size(), successCount.get(), failedCount.get());
            updateJobStatus(jobId, finalStatus);

            log.info("========================================");
            log.info("[并行审核完成] jobId={}, status={}", jobId, finalStatus);
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
