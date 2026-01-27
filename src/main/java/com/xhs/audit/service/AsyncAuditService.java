package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.repository.AuditJobRepository;

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
        log.info("开始异步处理审核任务: jobId={}, totalLinks={}", jobId, urls.size());

        try {
            // 更新任务状态为PROCESSING
            updateJobStatus(jobId, "PROCESSING");

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            // 逐条处理链接
            for (String url : urls) {
                try {
                    // 审核单条内容
                    AuditDecision decision = contentAuditService.auditContent(url, false);

                    // 统计结果
                    if ("PASSED".equals(decision.getStatus())) {
                        successCount.incrementAndGet();
                    } else if ("REJECTED".equals(decision.getStatus())) {
                        failedCount.incrementAndGet();
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
                        log.info("任务进度: jobId={}, completed={}/{}",
                                jobId, completed, urls.size());
                    }
                }
            }

            // 任务完成
            String finalStatus = (failedCount.get() == 0) ? "COMPLETED" : "PARTIAL_SUCCESS";
            updateJobStatus(jobId, finalStatus);

            log.info("审核任务完成: jobId={}, status={}, success={}, failed={}",
                    jobId, finalStatus, successCount.get(), failedCount.get());

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
