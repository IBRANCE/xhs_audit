package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.repository.AuditJobRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 异步审核服务
 * 处理批量审核任务的异步执行
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

    /**
     * 异步处理审核任务
     * 
     * @param jobId 任务ID
     * @param urls  待审核的URL列表
     */
    @Async("taskExecutor")
    public void processAuditJob(String jobId, List<String> urls) {
        log.info("========================================");
        log.info("[异步阶段] 开始后台处理: jobId={}, totalLinks={}", jobId, urls.size());
        log.info("========================================");

        try {
            // 更新任务状态为PROCESSING
            log.info("[异步阶段] 更新任务状态: jobId={}, PENDING -> PROCESSING", jobId);
            updateJobStatus(jobId, "PROCESSING");

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            // 批处理循环：每批处理链接
            log.info("[异步阶段] 开始批处理循环: 共{}条链接待处理", urls.size());
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                try {
                    log.info("[异步阶段] 处理第{}/{}条链接: {}", i + 1, urls.size(), url);
                    // 审核单条内容，传入 jobId 用于关联
                    AuditDecision decision = contentAuditService.auditContent(url, false, jobId);

                    // 统计结果
                    if ("PASSED".equals(decision.getStatus())) {
                        successCount.incrementAndGet();
                        log.debug("[异步阶段] 审核通过: postId={}", decision.getPostId());
                    } else if ("REJECTED".equals(decision.getStatus())) {
                        failedCount.incrementAndGet();
                        log.debug("[异步阶段] 审核驳回: postId={}, reasons={}",
                                decision.getPostId(), decision.getReasons());
                    }

                } catch (Exception e) {
                    log.error("单条链接审核失败: url={}, error={}", url, e.getMessage());
                    failedCount.incrementAndGet();
                } finally {
                    // 更新进度
                    int completed = completedCount.incrementAndGet();

                    // 每处理10条更新一次数据库
                    if (completed % 10 == 0 || completed == urls.size()) {
                        updateJobProgress(jobId, completed, successCount.get(), failedCount.get());
                        log.info("[异步阶段] 更新任务进度: jobId={}, 已完成={}/{}, 成功={}, 失败={}",
                                jobId, completed, urls.size(), successCount.get(), failedCount.get());
                    }
                }
            }

            // 任务完成
            String finalStatus = (failedCount.get() == 0) ? "COMPLETED" : "PARTIAL_SUCCESS";
            log.info("[异步阶段] 批处理完成，更新最终状态: jobId={}, PROCESSING -> {}", jobId, finalStatus);
            updateJobStatus(jobId, finalStatus);

            log.info("========================================");
            log.info("[异步阶段完成] jobId={}, status={}, 总计={}, 成功={}, 失败={}",
                    jobId, finalStatus, urls.size(), successCount.get(), failedCount.get());
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
