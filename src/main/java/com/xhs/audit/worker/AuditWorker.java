package com.xhs.audit.worker;

import static com.xhs.audit.config.RedisStreamConstants.LOCK_PREFIX_AUDIT;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xhs.audit.agent.ContentAuditAgent;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.dto.AuditTaskMessage;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.model.entity.TaskStatus;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;
import com.xhs.audit.repository.XhsContentRepository;
import com.xhs.audit.util.AuditResultConverter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 审核 Worker - 消费审核任务
 * v4.0: 支持断路器保护、重试、指标上报
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
@Component
@ConditionalOnProperty(prefix = "audit.worker", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class AuditWorker {

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private ContentAuditAgent contentAuditAgent;

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired
    private XhsContentRepository xhsContentRepository;

    @Autowired
    private AuditResultRepository auditResultRepository;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${audit.worker.type:both}")
    private String workerType;

    @Value("${spring.data.redis.stream.batch-size:10}")
    private int batchSize;

    @Value("${spring.data.redis.stream.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${audit.worker.consumer-name-prefix:local}")
    private String consumerNamePrefix;

    @Value("${audit.lock.wait-time-seconds:30}")
    private int lockWaitTime;

    @Value("${audit.lock.lease-time-seconds:120}")
    private int lockLeaseTime;

    private final String workerId = UUID.randomUUID().toString().substring(0, 8);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 10;

    @PostConstruct
    private void init() {
        if (redissonClient == null && ("audit-worker".equals(workerType) || "both".equals(workerType))) {
            log.warn("[AuditWorker] Redisson 未配置，分布式锁将不生效，仅适用于单节点模式");
        }

        // 在独立线程中启动 Worker
        Thread workerThread = new Thread(this::startWorker, "AuditWorker-" + workerId);
        workerThread.setDaemon(false);
        workerThread.start();
    }

    private void startWorker() {
        log.info("[AuditWorker] 🔍 检查启动条件: workerType={}, enabled=true", workerType);

        // 仅在 audit-worker 或 both 模式下启动
        if (!"audit-worker".equals(workerType) && !"both".equals(workerType)) {
            log.info("[AuditWorker] ⏸️ 当前模式不启动审核 Worker: {}", workerType);
            return;
        }

        String consumerName = consumerNamePrefix + "-audit-" + workerId;
        log.info("[AuditWorker] 🚀 启动 Worker: consumerId={}, batchSize={}", consumerName, batchSize);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // XREADGROUP 已经使用 BLOCK 参数，会阻塞等待 2 秒，不需要额外 sleep
                List<MapRecord<String, Object, Object>> messages = messageQueueService.consumeAuditTasks(consumerName,
                        batchSize);

                if (messages.isEmpty()) {
                    // 没有消息，继续下一次阻塞等待
                    continue;
                }

                log.debug("[AuditWorker] 消费 {} 条消息", messages.size());

                for (MapRecord<String, Object, Object> message : messages) {
                    processMessage(message);
                }

            } catch (Exception e) {
                consecutiveErrors++;
                log.error("[AuditWorker] 消费循环异常 ({}/{})", consecutiveErrors, MAX_CONSECUTIVE_ERRORS, e);

                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    log.error("[AuditWorker] 连续错误次数过多，停止 Worker");
                    running.set(false);
                    break;
                }

                // 指数退避：5s, 10s, 20s, ..., 最多 60s
                long sleepTime = Math.min(5000L * consecutiveErrors, 60000L);
                log.info("[AuditWorker] 等待 {}ms 后重试", sleepTime);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[AuditWorker] Worker 被中断，退出");
                    break;
                }
            }
        }

        log.info("[AuditWorker] Worker 已停止: {}", workerId);
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        String messageId = message.getId().getValue();
        Map<Object, Object> value = message.getValue();

        String jobId = (String) value.get("jobId");
        String postId = (String) value.get("postId");
        String url = (String) value.get("url");
        int retryCount = Integer.parseInt((String) value.getOrDefault("retryCount", "0"));

        // 设置 MDC 追踪上下文
        MDC.put("jobId", jobId);
        MDC.put("url", url);

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("[AuditWorker] 🚀 开始审核任务");

            // v4.0: 第一次状态检查 - 快速过滤已完成的任务（无锁）
            var jobOpt = auditJobRepository.findByJobId(jobId);
            if (jobOpt.isPresent()) {
                String currentStatus = jobOpt.get().getStatus();
                if (TaskStatus.COMPLETED.name().equals(currentStatus)) {
                    log.info("[AuditWorker] ⏭️ 任务已完成，跳过");
                    messageQueueService.acknowledgeAuditTask(messageId);
                    meterRegistry.counter("audit.task.skipped", "reason", "already_completed").increment();
                    return;
                }
            }

            // v4.0: 分布式锁保护 - 防止同一 postId 被多个 Worker 并发处理
            // 🔧 如果将来改成多线程架构，锁的粒度应该是 postId 级别，而不是 Job 级别
            // 否则同一个 Job 的多个 postId 会互相阻塞
            RLock lock = null;
            if (redissonClient != null) {
                // 使用 postId 作为锁 key（postId 通常较短，不需要哈希）
                String lockKey = LOCK_PREFIX_AUDIT + "post:" + postId;
                lock = redissonClient.getLock(lockKey);
                boolean locked = lock.tryLock(lockWaitTime, lockLeaseTime, TimeUnit.SECONDS);

                if (!locked) {
                    log.warn("[AuditWorker] 🔒 获取分布式锁失败，该 postId 可能正在被其他节点处理");
                    // 不 ACK，让消息保持在 Pending 状态
                    return;
                }
            }

            try {
                // v4.0: 第二次状态检查 - 锁内再次确认（双重检查锁模式）
                jobOpt = auditJobRepository.findByJobId(jobId);
                if (jobOpt.isPresent()) {
                    String currentStatus = jobOpt.get().getStatus();
                    if (TaskStatus.COMPLETED.name().equals(currentStatus)) {
                        log.info("[AuditWorker] ⏭️ 锁内检查：任务已完成，跳过");
                        messageQueueService.acknowledgeAuditTask(messageId);
                        meterRegistry.counter("audit.task.skipped", "reason", "double_check_completed").increment();
                        return;
                    }
                }

                // 现在才安全地更新状态
                updateJobStatus(jobId, TaskStatus.AUDITING.name(), "AI审核中...");

                // 从数据库获取内容（应该已经由 CrawlerWorker 保存）
                XhsContent content = xhsContentRepository.findByPostId(postId)
                        .orElseThrow(() -> new IllegalStateException("内容未找到: " + postId));

                log.info("[AuditWorker] 🤖 调用 AI 审核...");

                // v4.0: 调用审核服务（已添加 @CircuitBreaker 和 @Retry 注解）
                AuditDecision decision = contentAuditAgent.auditContent(content);

                log.info("[AuditWorker] ✅ AI 审核完成: risk={}, action={}",
                        decision.getRiskLevel(), decision.getSuggestedAction());

                // 保存审核结果到数据库
                saveAuditResult(decision, url, jobId);
                log.info("[AuditWorker] 💾 审核结果已保存: postId={}", postId);

                // 更新任务计数器
                updateJobProgress(jobId, decision.getStatus());

                // 检查任务是否全部完成，如果是则更新任务状态为COMPLETED
                checkAndUpdateJobCompletion(jobId);

                // 确认消息
                messageQueueService.acknowledgeAuditTask(messageId);

                // v4.0: 指标上报
                sample.stop(meterRegistry.timer("audit.processing.duration", "status", "success"));
                meterRegistry.counter("audit.task.success",
                        "risk_level", decision.getRiskLevel()).increment();

                // 重置错误计数
                consecutiveErrors = 0;

                log.info("[AuditWorker] ✅ 审核任务完成");

            } finally {
                if (lock != null && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (Exception e) {
            log.error("[AuditWorker] ❌ 失败: retry={}", retryCount, e);

            // v4.0: 重试策略
            if (retryCount < maxRetryAttempts) {
                log.info("[AuditWorker] 🔄 将重试任务: retry={}", retryCount + 1);
                updateJobStatus(jobId, TaskStatus.RETRYING.name(),
                        "审核失败，重试中 (" + (retryCount + 1) + "/" + maxRetryAttempts + ")");

                // 重新发送消息到队列
                AuditTaskMessage retryMessage = new AuditTaskMessage(
                        UUID.randomUUID().toString(),
                        jobId,
                        postId,
                        url,
                        LocalDateTime.now(),
                        retryCount + 1);
                messageQueueService.sendAuditTask(retryMessage);
            } else {
                // 超过最大重试次数，移入死信队列
                updateJobStatus(jobId, TaskStatus.FAILED.name(), "审核失败: " + e.getMessage());
                Map<String, String> originalMessage = value.entrySet().stream()
                        .collect(Collectors.toMap(
                                e2 -> e2.getKey().toString(),
                                e2 -> e2.getValue().toString()));
                messageQueueService.sendToDeadLetterQueue("audit", jobId, e.getMessage(), originalMessage);
                log.warn("[AuditWorker] ☠️☠️☠️ 死信队列 ☠️☠️☠️  jobId={}, reason={}", jobId, e.getMessage());
            }

            // 确认消息（避免重复处理）
            messageQueueService.acknowledgeAuditTask(messageId);

            // v4.0: 失败指标
            sample.stop(meterRegistry.timer("audit.processing.duration", "status", "failed"));
            meterRegistry.counter("audit.task.failed", "reason", e.getClass().getSimpleName()).increment();
        } finally {
            MDC.clear();
        }
    }

    /**
     * 保存审核结果
     */
    @Transactional
    private void saveAuditResult(AuditDecision decision, String url, String jobId) {
        try {
            AuditResult result = new AuditResult();
            result.setPostId(decision.getPostId());
            result.setUrl(url);
            result.setJobId(jobId);
            result.setAuditStatus(decision.getStatus());
            result.setReasons(AuditResultConverter.reasonsToList(decision.getReasons()));
            result.setConfidenceScore(
                    decision.getConfidenceScore() != null
                            ? java.math.BigDecimal.valueOf(decision.getConfidenceScore())
                            : null);
            result.setModelName(decision.getModelName());
            result.setAuditedAt(LocalDateTime.now());

            auditResultRepository.save(result);
            log.debug("[AuditWorker] 审核结果已保存: postId={}, jobId={}", decision.getPostId(), jobId);
        } catch (Exception e) {
            log.error("[AuditWorker] 保存审核结果失败: postId={}, jobId={}", decision.getPostId(), jobId, e);
            throw e;
        }
    }

    /**
     * 更新任务状态
     * v4.0: 添加乐观锁保护，防止并发更新
     */
    @Transactional
    private void updateJobStatus(String jobId, String status, String message) {
        try {
            auditJobRepository.findByJobId(jobId).ifPresent(job -> {
                String currentStatus = job.getStatus();

                // 使用状态机验证转换
                if (!TaskStatus.isAllowedTransition(currentStatus, status)) {
                    log.warn("[AuditWorker] 非法状态转换: {} -> {}", currentStatus, status);
                    return;
                }

                job.setStatus(status);
                job.setMessage(message);
                job.setUpdatedAt(LocalDateTime.now());
                auditJobRepository.save(job);
                log.debug("[AuditWorker] 状态更新: {} -> {}", currentStatus, status);
            });
        } catch (Exception e) {
            log.error("[AuditWorker] 更新任务状态失败: jobId={}", jobId, e);
        }
    }

    /**
     * 更新任务进度（增加完成计数和成功/失败计数）
     */
    @Transactional
    private void updateJobProgress(String jobId, String auditStatus) {
        try {
            auditJobRepository.findByJobId(jobId).ifPresent(job -> {
                // 增加完成计数
                job.incrementCompletedCount();

                // 根据审核状态增加成功或失败计数
                if ("PASSED".equals(auditStatus)) {
                    job.incrementSuccessCount();
                    log.debug("[AuditWorker] 审核通过: jobId={}, 通过数={}", jobId, job.getSuccessCount());
                } else if ("REJECTED".equals(auditStatus)) {
                    job.incrementFailedCount();
                    log.debug("[AuditWorker] 审核驳回: jobId={}, 驳回数={}", jobId, job.getFailedCount());
                } else {
                    // UNCERTAIN 等其他状态也算失败
                    job.incrementFailedCount();
                    log.debug("[AuditWorker] 审核结果异常: jobId={}, 状态={}", jobId, auditStatus);
                }

                job.setUpdatedAt(LocalDateTime.now());
                auditJobRepository.save(job);

                log.info("[AuditWorker] 进度更新: jobId={}, 已完成={}/{}, 通过={}, 驳回={}",
                        jobId, job.getCompletedCount(), job.getTotalLinks(),
                        job.getSuccessCount(), job.getFailedCount());
            });
        } catch (Exception e) {
            log.error("[AuditWorker] 更新任务进度失败: jobId={}", jobId, e);
        }
    }

    /**
     * 检查任务是否全部完成，如果是则更新状态为COMPLETED
     */
    @Transactional
    private void checkAndUpdateJobCompletion(String jobId) {
        try {
            auditJobRepository.findByJobId(jobId).ifPresent(job -> {
                // 检查是否所有链接都已处理完成
                if (job.getCompletedCount() >= job.getTotalLinks()) {
                    // 判断最终状态
                    String finalStatus;
                    if (job.getFailedCount() == 0) {
                        finalStatus = "COMPLETED";
                    } else if (job.getSuccessCount() == 0) {
                        finalStatus = "FAILED";
                    } else {
                        finalStatus = "PARTIAL_SUCCESS";
                    }

                    job.setStatus(finalStatus);
                    job.setCompletedAt(LocalDateTime.now());
                    job.setMessage(String.format("任务完成: 总计=%d, 通过=%d, 驳回=%d",
                            job.getTotalLinks(), job.getSuccessCount(), job.getFailedCount()));
                    job.setUpdatedAt(LocalDateTime.now());
                    auditJobRepository.save(job);

                    log.info("[AuditWorker] 🎉 任务全部完成: jobId={}, status={}, 总计={}, 通过={}, 驳回={}",
                            jobId, finalStatus, job.getTotalLinks(), job.getSuccessCount(), job.getFailedCount());
                }
            });
        } catch (Exception e) {
            log.error("[AuditWorker] 检查任务完成状态失败: jobId={}", jobId, e);
        }
    }

    /**
     * v4.0: 优雅停机
     */
    @PreDestroy
    public void shutdown() {
        log.info("[AuditWorker] 开始优雅停机: {}", workerId);
        running.set(false);

        // 等待当前任务完成（最多等待 30 秒）
        int waitCount = 0;
        while (waitCount < 30) {
            try {
                Thread.sleep(1000);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("[AuditWorker] 优雅停机完成: {}", workerId);
    }
}
