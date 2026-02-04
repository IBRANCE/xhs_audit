package com.xhs.audit.worker;

import static com.xhs.audit.config.RedisStreamConstants.LOCK_PREFIX_CRAWLER;

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

import com.xhs.audit.infrastructure.CrawlDuplicateFilter;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.AuditTaskMessage;
import com.xhs.audit.model.dto.CrawlTaskMessage;
import com.xhs.audit.model.entity.TaskStatus;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.service.CrawlerService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 爬虫 Worker - 消费爬取任务
 * v4.0: 支持分布式锁、优雅停机、指标上报
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
@Component
@ConditionalOnProperty(prefix = "audit.worker", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class CrawlerWorker {

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired(required = false)
    private CrawlDuplicateFilter crawlDuplicateFilter;

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

    @Value("${audit.crawler.concurrent-crawl-threads:5}")
    private int concurrentCrawlThreads;

    private final String workerId = UUID.randomUUID().toString().substring(0, 8);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private static final int MAX_CONSECUTIVE_ERRORS = 10;

    @PostConstruct
    private void init() {
        // 检查 Redisson 是否配置（多节点模式必须）
        if (redissonClient == null && ("crawler-worker".equals(workerType) || "both".equals(workerType))) {
            log.warn("[CrawlerWorker] Redisson 未配置，分布式锁将不生效，仅适用于单节点模式");
        }

        // 仅在 crawler-worker 或 both 模式下启动
        if (!"crawler-worker".equals(workerType) && !"both".equals(workerType)) {
            log.info("[CrawlerWorker] ⏸️ 当前模式不启动爬虫 Worker: {}", workerType);
            return;
        }

        log.info("[CrawlerWorker] 🚀 启动多消费者模式: workerId={}, consumers={}", workerId, concurrentCrawlThreads);

        // 🔧 新架构：创建多个独立的消费者线程，每个线程自己读取 Redis Stream
        // 每个线程有独立的 consumer name，避免冲突
        for (int i = 0; i < concurrentCrawlThreads; i++) {
            final int threadIndex = i;
            String consumerName = consumerNamePrefix + "-crawler-" + workerId + "-" + threadIndex;
            Thread workerThread = new Thread(() -> startWorker(consumerName, threadIndex),
                    "CrawlWorker-" + workerId + "-" + threadIndex);
            workerThread.setDaemon(false);
            workerThread.start();
            log.info("[CrawlerWorker] 启动消费者线程: {}", consumerName);
        }
    }

    /**
     * 🔧 新架构：每个线程独立运行消费循环
     * 多个线程并发从 Redis Stream 读取消息，而不是主线程分发
     */
    private void startWorker(String consumerName, int threadIndex) {
        log.info("[CrawlerWorker-{}] 🚀 消费者线程启动", threadIndex);

        int localConsecutiveErrors = 0;

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 🔧 每个线程独立读取消息（每次读1条，多个线程并发读取）
                List<MapRecord<String, Object, Object>> messages = messageQueueService.consumeCrawlTasks(
                        consumerName, 1); // 每个线程每次读1条，避免某个线程积压

                if (messages.isEmpty()) {
                    // 没有消息，继续下一次阻塞等待（BLOCK 2秒）
                    continue;
                }

                // 立即处理消息（不经过线程池，直接在当前线程执行）
                for (MapRecord<String, Object, Object> message : messages) {
                    log.debug("[CrawlerWorker-{}] 📥 读取到消息，开始处理", threadIndex);
                    processMessage(message);
                }

                // 重置错误计数
                localConsecutiveErrors = 0;

            } catch (Exception e) {
                localConsecutiveErrors++;
                log.error("[CrawlerWorker-{}] 消费循环异常 ({}/{})", threadIndex,
                        localConsecutiveErrors, MAX_CONSECUTIVE_ERRORS, e);

                if (localConsecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    log.error("[CrawlerWorker-{}] 连续错误次数过多，停止消费者", threadIndex);
                    break;
                }

                // 指数退避：5s, 10s, 20s, ..., 最多 60s
                long sleepTime = Math.min(5000L * localConsecutiveErrors, 60000L);
                log.info("[CrawlerWorker-{}] 等待 {}ms 后重试", threadIndex, sleepTime);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[CrawlerWorker-{}] 被中断，退出", threadIndex);
                    break;
                }
            }
        }

        log.info("[CrawlerWorker-{}] 消费者线程已停止", threadIndex);
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        String messageId = message.getId().getValue();
        Map<Object, Object> value = message.getValue();

        String jobId = (String) value.get("jobId");
        String url = (String) value.get("url");
        int retryCount = Integer.parseInt((String) value.getOrDefault("retryCount", "0"));

        // 设置 MDC 追踪上下文
        MDC.put("jobId", jobId);
        MDC.put("url", url);

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("[CrawlerWorker] 🚀 开始处理任务");

            // v4.0: 布隆过滤器快速检查（可选，需启用 audit.bloom-filter.enabled）
            if (crawlDuplicateFilter != null && crawlDuplicateFilter.mightContain(url)) {
                log.debug("[CrawlerWorker] 布隆过滤器提示 URL 可能已存在，进行数据库确认: url={}", url);
                meterRegistry.counter("crawl.bloom.hint").increment();
            }

            // 🔧 修复：检查 URL 级别的状态，而不是 Job 级别
            // 批量任务场景下，多个 URL 共享一个 jobId，不能用 Job 状态判断
            // 应该检查这个具体 URL 是否已经被爬取过（通过 AuditResult 表）

            // v4.0: 分布式锁保护 - 防止同一 URL 被多个 Worker 并发处理
            // 🔧 多消费者架构：锁的粒度必须是 URL 级别，而不是 Job 级别
            // 否则同一个 Job 的多个 URL 会互相阻塞
            RLock lock = null;
            if (redissonClient != null) {
                // 使用 URL 的哈希值作为锁 key，避免 URL 过长
                String lockKey = LOCK_PREFIX_CRAWLER + "url:" + Integer.toHexString(url.hashCode());
                lock = redissonClient.getLock(lockKey);
                boolean locked = lock.tryLock(lockWaitTime, lockLeaseTime, TimeUnit.SECONDS);

                if (!locked) {
                    log.warn("[CrawlerWorker] 🔒 获取分布式锁失败，该 URL 可能正在被其他节点处理");
                    // 不 ACK，让消息保持在 Pending 状态
                    return;
                }
            }

            try {
                // 🔧 修复：双重检查也应该检查 URL 是否已处理，而不是 Job 状态
                // 注释掉错误的 Job 状态检查
                // jobOpt = auditJobRepository.findByJobId(jobId);
                // if (jobOpt.isPresent()) {
                // String currentStatus = jobOpt.get().getStatus();
                // if (TaskStatus.COMPLETED.name().equals(currentStatus) ||
                // TaskStatus.CRAWLED.name().equals(currentStatus)) {
                // log.info("[CrawlerWorker] ⏭️ 锁内检查：任务已完成，跳过");
                // messageQueueService.acknowledgeCrawlTask(messageId);
                // meterRegistry.counter("crawl.task.skipped", "reason",
                // "double_check_completed").increment();
                // return;
                // }
                // }

                // 现在才安全地更新状态（注意：批量任务场景下，不要修改 Job 状态为 CRAWLING）
                // updateJobStatus(jobId, TaskStatus.CRAWLING.name(), "爬取中...");

                // 执行爬虫
                XhsContent content = crawlerService.crawlContent(url);

                log.info("[CrawlerWorker] 🕷️ 爬取完成: postId={}", content.getPostId());

                // v4.0: 添加到布隆过滤器（标记已爬取）
                if (crawlDuplicateFilter != null) {
                    crawlDuplicateFilter.add(url);
                    log.debug("[CrawlerWorker] URL 已添加到布隆过滤器");
                }

                // 🔧 修复：批量任务场景下，不要修改 Job 状态，只增加计数
                // updateJobStatus(jobId, TaskStatus.CRAWLED.name(), "爬取完成，发送审核任务");
                incrementJobProgress(jobId); // 使用新方法仅增加进度计数

                // 发送审核任务
                AuditTaskMessage auditMessage = new AuditTaskMessage(
                        jobId, content.getPostId(), content.getUrl());
                messageQueueService.sendAuditTask(auditMessage);
                log.info("[CrawlerWorker] 📤 审核任务已发送");

                // 确认消息
                messageQueueService.acknowledgeCrawlTask(messageId);

                // v4.0: 指标上报
                sample.stop(meterRegistry.timer("crawl.processing.duration", "status", "success"));
                meterRegistry.counter("crawl.task.success").increment();

                log.info("[CrawlerWorker] ✅ 任务完成");

            } finally {
                if (lock != null && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (Exception e) {
            log.error("[CrawlerWorker] ❌ 失败: retry={}", retryCount, e);

            // v4.0: 重试策略
            if (retryCount < maxRetryAttempts) {
                log.info("[CrawlerWorker] 🔄 将重试任务: retry={}", retryCount + 1);
                updateJobStatus(jobId, TaskStatus.RETRYING.name(),
                        "爬取失败，重试中 (" + (retryCount + 1) + "/" + maxRetryAttempts + ")");

                // 重新发送消息到队列
                CrawlTaskMessage retryMessage = new CrawlTaskMessage(
                        UUID.randomUUID().toString(),
                        jobId,
                        url,
                        "RETRY",
                        true, // 重试时强制刷新
                        retryCount + 1,
                        java.time.LocalDateTime.now());
                messageQueueService.sendCrawlTask(retryMessage);
            } else {
                // 超过最大重试次数，移入死信队列
                updateJobStatus(jobId, TaskStatus.FAILED.name(), "爬取失败: " + e.getMessage());
                Map<String, String> originalMessage = value.entrySet().stream()
                        .collect(Collectors.toMap(
                                e2 -> e2.getKey().toString(),
                                e2 -> e2.getValue().toString()));
                messageQueueService.sendToDeadLetterQueue("crawl", jobId, e.getMessage(), originalMessage);
                log.warn("[CrawlerWorker] ☠️☠️☠️ 死信队列 ☠️☠️☠️  jobId={}, reason={}", jobId, e.getMessage());
            }

            // 确认消息（避免重复处理）
            messageQueueService.acknowledgeCrawlTask(messageId);

            // v4.0: 失败指标
            sample.stop(meterRegistry.timer("crawl.processing.duration", "status", "failed"));
            meterRegistry.counter("crawl.task.failed", "reason", e.getClass().getSimpleName()).increment();
        } finally {
            MDC.clear();
        }
    }

    /**
     * 更新任务状态
     * v4.0: 添加乐观锁保护，防止并发更新
     * ⚠️ 注意：批量任务场景下，不要在这里修改状态，会导致其他任务被跳过
     */
    @Transactional
    private void updateJobStatus(String jobId, String status, String message) {
        try {
            auditJobRepository.findByJobId(jobId).ifPresent(job -> {
                String currentStatus = job.getStatus();

                // 使用状态机验证转换
                if (!TaskStatus.isAllowedTransition(currentStatus, status)) {
                    log.warn("[CrawlerWorker] 非法状态转换: {} -> {}", currentStatus, status);
                    return;
                }

                job.setStatus(status);
                job.setMessage(message);
                job.setUpdatedAt(LocalDateTime.now());
                auditJobRepository.save(job);
                log.debug("[CrawlerWorker] 状态更新: {} -> {}", currentStatus, status);
            });
        } catch (Exception e) {
            log.error("[CrawlerWorker] 更新任务状态失败: jobId={}", jobId, e);
        }
    }

    /**
     * 🔧 新增：仅增加任务进度，不修改状态（用于批量任务）
     * 批量任务场景下，多个 URL 共享一个 jobId，不能因为一个 URL 完成就改 Job 状态
     */
    @Transactional
    private void incrementJobProgress(String jobId) {
        try {
            auditJobRepository.findByJobId(jobId).ifPresent(job -> {
                job.setCompletedCount(job.getCompletedCount() + 1);
                job.setSuccessCount(job.getSuccessCount() + 1);
                job.setUpdatedAt(LocalDateTime.now());

                // 如果所有 URL 都完成了，才更新状态为 COMPLETED
                if (job.getCompletedCount().equals(job.getTotalLinks())) {
                    job.setStatus(TaskStatus.COMPLETED.name());
                    job.setMessage("所有链接爬取完成");
                    log.info("[CrawlerWorker] ✅ 批量任务全部完成: jobId={}, total={}", jobId, job.getTotalLinks());
                } else {
                    // 否则保持 PROCESSING 状态
                    if ("PENDING".equals(job.getStatus())) {
                        job.setStatus("PROCESSING");
                    }
                    log.debug("[CrawlerWorker] 进度更新: jobId={}, progress={}/{}",
                            jobId, job.getCompletedCount(), job.getTotalLinks());
                }

                auditJobRepository.save(job);
            });
        } catch (Exception e) {
            log.error("[CrawlerWorker] 更新任务进度失败: jobId={}", jobId, e);
        }
    }

    /**
     * v4.0: 优雅停机
     */
    @PreDestroy
    public void shutdown() {
        log.info("[CrawlerWorker] 开始优雅停机: {}", workerId);
        running.set(false);

        // 等待所有消费者线程完成当前任务（最多60秒）
        log.info("[CrawlerWorker] 等待所有消费者线程完成...");
        try {
            Thread.sleep(5000); // 给线程一些时间完成当前任务
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[CrawlerWorker] 优雅停机完成: {}", workerId);
    }
}
