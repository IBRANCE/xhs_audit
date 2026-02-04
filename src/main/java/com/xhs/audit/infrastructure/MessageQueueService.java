package com.xhs.audit.infrastructure;

import static com.xhs.audit.config.RedisStreamConstants.AUDIT_GROUP;
import static com.xhs.audit.config.RedisStreamConstants.AUDIT_STREAM;
import static com.xhs.audit.config.RedisStreamConstants.CRAWL_GROUP;
import static com.xhs.audit.config.RedisStreamConstants.CRAWL_STREAM;
import static com.xhs.audit.config.RedisStreamConstants.DEAD_LETTER_STREAM;
import static com.xhs.audit.config.RedisStreamConstants.DEDUPE_PREFIX_CRAWL;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.xhs.audit.model.dto.AuditTaskMessage;
import com.xhs.audit.model.dto.CrawlTaskMessage;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Stream 消息队列服务
 * v4.0: 支持幂等性、死信队列、Pending 消息回收、监控指标
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
@Service
@Slf4j
public class MessageQueueService {

    @Value("${spring.data.redis.stream.pending-retry-interval-ms:60000}")
    private long pendingRetryIntervalMs;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * 发送爬虫任务
     * v4.0: 添加幂等性检查，但重试任务跳过去重
     * 🔧 修复：去重策略改为 jobId+URL，而不是仅 URL
     * - 不同批次的相同 URL 可以重新处理
     * - 同一批次内的重复 URL 会被过滤
     */
    public String sendCrawlTask(CrawlTaskMessage task) {
        // v4.0: 幂等性检查 - 重试任务跳过去重
        if (task.retryCount() == 0) {
            // 🔧 修复：使用 jobId + URL 作为去重键，而不是仅 URL
            // 原因：不同批次任务可能包含相同 URL，应该允许重新处理
            String dedupeKey = DEDUPE_PREFIX_CRAWL + task.jobId() + ":" + task.url();

            // TTL 设置为 24 小时即可（单批次任务处理时长）
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(
                    dedupeKey,
                    task.messageId(), // 存储 messageId 用于追踪
                    Duration.ofHours(24) // 改为 24 小时
            );

            if (Boolean.FALSE.equals(isNew)) {
                String existingMessageId = redisTemplate.opsForValue().get(dedupeKey);
                log.warn("[MQ] 批次内重复任务，已跳过: jobId={}, url={}, existingMessageId={}",
                        task.jobId(), task.url(), existingMessageId);
                meterRegistry.counter("mq.crawl.task.duplicate").increment();
                return null;
            }
        } else {
            log.info("[MQ] 重试任务跳过去重检查: url={}, retryCount={}", task.url(), task.retryCount());
        }

        Map<String, String> fields = new HashMap<>();
        fields.put("messageId", task.messageId());
        fields.put("jobId", task.jobId());
        fields.put("url", task.url());
        fields.put("source", task.source());
        fields.put("forceRefresh", String.valueOf(task.forceRefresh()));
        fields.put("retryCount", String.valueOf(task.retryCount()));
        fields.put("createdAt", task.createdAt().toString());

        RecordId recordId = redisTemplate.opsForStream().add(CRAWL_STREAM, fields);

        // v4.0: 指标上报
        meterRegistry.counter("mq.crawl.task.submitted").increment();

        log.info("[MQ] 📤 爬虫任务已发送到队列: stream={}, recordId={}", CRAWL_STREAM, recordId);
        return recordId.getValue();
    }

    /**
     * 发送审核任务
     */
    public void sendAuditTask(AuditTaskMessage task) {
        Map<String, String> fields = new HashMap<>();
        fields.put("messageId", task.messageId());
        fields.put("jobId", task.jobId());
        fields.put("postId", task.postId());
        fields.put("url", task.url());
        fields.put("retryCount", String.valueOf(task.retryCount()));
        fields.put("createdAt", task.createdAt().toString());

        redisTemplate.opsForStream().add(AUDIT_STREAM, fields);

        // v4.0: 指标上报
        meterRegistry.counter("mq.audit.task.submitted").increment();

        log.info("[MQ] 📤 审核任务已发送到队列: stream={}", AUDIT_STREAM);
    }

    /**
     * 消费爬虫任务 (批量阻塞式)
     */
    public List<MapRecord<String, Object, Object>> consumeCrawlTasks(String consumerName, int batchSize) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(
                            Consumer.from(CRAWL_GROUP, consumerName),
                            StreamReadOptions.empty().count(batchSize).block(Duration.ofSeconds(2)),
                            StreamOffset.create(CRAWL_STREAM, ReadOffset.lastConsumed()));

            if (records != null && !records.isEmpty()) {
                // v4.0: 队列深度指标
                Long queueSize = redisTemplate.opsForStream().size(CRAWL_STREAM);
                meterRegistry.gauge("mq.crawl.queue.size", queueSize != null ? queueSize : 0);
                log.debug("[MQ] 📥 从 {} 消费 {} 条消息", CRAWL_STREAM, records.size());
            }

            return records != null ? records : List.of();
        } catch (Exception e) {
            log.trace("[MQ] 等待爬虫任务中...");
            return List.of();
        }
    }

    /**
     * 消费审核任务 (批量阻塞式)
     */
    public List<MapRecord<String, Object, Object>> consumeAuditTasks(String consumerName, int batchSize) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(
                            Consumer.from(AUDIT_GROUP, consumerName),
                            StreamReadOptions.empty().count(batchSize).block(Duration.ofSeconds(2)),
                            StreamOffset.create(AUDIT_STREAM, ReadOffset.lastConsumed()));

            if (records != null && !records.isEmpty()) {
                Long queueSize = redisTemplate.opsForStream().size(AUDIT_STREAM);
                meterRegistry.gauge("mq.audit.queue.size", queueSize != null ? queueSize : 0);
                log.debug("[MQ] 📥 从 {} 消费 {} 条消息", AUDIT_STREAM, records.size());
            }

            return records != null ? records : List.of();
        } catch (Exception e) {
            log.trace("[MQ] 等待审核任务中...");
            return List.of();
        }
    }

    /**
     * 确认爬虫任务完成
     */
    public void acknowledgeCrawlTask(String messageId) {
        redisTemplate.opsForStream().acknowledge(CRAWL_STREAM, CRAWL_GROUP, messageId);
        meterRegistry.counter("mq.crawl.task.acked").increment();
    }

    /**
     * 确认审核任务完成
     */
    public void acknowledgeAuditTask(String messageId) {
        redisTemplate.opsForStream().acknowledge(AUDIT_STREAM, AUDIT_GROUP, messageId);
        meterRegistry.counter("mq.audit.task.acked").increment();
    }

    /**
     * v4.0: 发送失败任务到死信队列
     */
    public void sendToDeadLetterQueue(String stream, String jobId, String reason, Map<String, String> originalMessage) {
        Map<String, String> fields = new HashMap<>();
        fields.put("stream", stream);
        fields.put("jobId", jobId);
        fields.put("reason", reason);
        fields.put("originalMessage", originalMessage.toString());
        fields.put("timestamp", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForStream().add(DEAD_LETTER_STREAM, fields);
        meterRegistry.counter("mq.dead.letter.count", "stream", stream).increment();

        log.warn("[MQ] 任务移入死信队列: stream={}, jobId={}, reason={}", stream, jobId, reason);
    }

    /**
     * v4.0: 定时回收超时的 Pending 消息
     * 如果消息在 Pending 状态超过 5 分钟未 ACK，则重新投递
     */
    @Scheduled(fixedDelayString = "${spring.data.redis.stream.pending-retry-interval-ms:60000}")
    public void reclaimPendingMessages() {
        reclaimPendingMessagesForStream(CRAWL_STREAM, CRAWL_GROUP, "爬虫");
        reclaimPendingMessagesForStream(AUDIT_STREAM, AUDIT_GROUP, "审核");
    }

    private void reclaimPendingMessagesForStream(String stream, String group, String queueName) {
        try {
            // 查询 Pending 消息摘要
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(stream, group);

            if (summary == null || summary.getTotalPendingMessages() == 0) {
                log.debug("[MQ] {} 队列无 Pending 消息", queueName);
                meterRegistry.gauge("mq." + queueName.toLowerCase() + ".pending.count", 0);
                return;
            }

            long totalPending = summary.getTotalPendingMessages();

            // 查询详细的 Pending 消息（最多 100 条）
            var pendingMessages = redisTemplate.opsForStream()
                    .pending(stream,
                            org.springframework.data.redis.connection.stream.Consumer.from(group, "*"),
                            Range.unbounded(),
                            100L);

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                log.warn("[MQ] {} 队列 Pending 摘要显示 {} 条，但详细查询为空，可能存在僵尸消息", queueName, totalPending);
                meterRegistry.gauge("mq." + queueName.toLowerCase() + ".pending.count", totalPending);
                return;
            }

            log.debug("[MQ] {} 队列 Pending 消息数: {} (实际可查: {})", queueName, totalPending, pendingMessages.size());
            meterRegistry.gauge("mq." + queueName.toLowerCase() + ".pending.count", totalPending);

            int reclaimedCount = 0;
            long idleTimeThresholdMs = 5 * 60 * 1000; // 5 分钟

            for (var pending : pendingMessages) {
                // 检查消息是否超时（空闲时间 > 5 分钟）
                if (pending.getElapsedTimeSinceLastDelivery().toMillis() > idleTimeThresholdMs) {
                    try {
                        // Claim 消息到 reclaim-worker
                        var claimed = redisTemplate.opsForStream().claim(
                                stream,
                                group,
                                "reclaim-worker",
                                Duration.ofMinutes(5),
                                org.springframework.data.redis.connection.stream.RecordId.of(pending.getIdAsString()));

                        if (claimed != null && !claimed.isEmpty()) {
                            reclaimedCount++;
                            log.info("[MQ] Reclaimed {} 消息: id={}, consumer={}, idleTime={}ms",
                                    queueName,
                                    pending.getIdAsString(),
                                    pending.getConsumerName(),
                                    pending.getElapsedTimeSinceLastDelivery().toMillis());

                            // 立即 ACK，让消息重新进入队列
                            if (stream.equals(CRAWL_STREAM)) {
                                acknowledgeCrawlTask(pending.getIdAsString());
                            } else {
                                acknowledgeAuditTask(pending.getIdAsString());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[MQ] Claim 消息失败: id={}", pending.getIdAsString(), e);
                    }
                }
            }

            if (reclaimedCount > 0) {
                log.info("[MQ] {} 队列回收了 {} 条超时消息", queueName, reclaimedCount);
                meterRegistry.counter("mq." + queueName.toLowerCase() + ".reclaimed").increment(reclaimedCount);
            }

        } catch (Exception e) {
            log.error("[MQ] {} 队列 Pending 消息回收失败", queueName, e);
        }
    }

    /**
     * v4.0: 查询死信队列
     */
    public List<MapRecord<String, Object, Object>> queryDeadLetterQueue(int count) {
        try {
            return redisTemplate.opsForStream()
                    .range(DEAD_LETTER_STREAM, Range.unbounded(),
                            org.springframework.data.redis.connection.RedisZSetCommands.Limit.limit().count(count));
        } catch (Exception e) {
            log.error("[MQ] 查询死信队列失败", e);
            return List.of();
        }
    }

    /**
     * 获取队列统计信息
     */
    public Map<String, Object> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            Long crawlSize = redisTemplate.opsForStream().size(CRAWL_STREAM);
            Long auditSize = redisTemplate.opsForStream().size(AUDIT_STREAM);
            Long dlqSize = redisTemplate.opsForStream().size(DEAD_LETTER_STREAM);

            stats.put("crawlQueueSize", crawlSize != null ? crawlSize : 0);
            stats.put("auditQueueSize", auditSize != null ? auditSize : 0);
            stats.put("deadLetterQueueSize", dlqSize != null ? dlqSize : 0);

            PendingMessagesSummary crawlPending = redisTemplate.opsForStream()
                    .pending(CRAWL_STREAM, CRAWL_GROUP);
            stats.put("crawlPendingCount",
                    crawlPending != null ? crawlPending.getTotalPendingMessages() : 0);

            PendingMessagesSummary auditPending = redisTemplate.opsForStream()
                    .pending(AUDIT_STREAM, AUDIT_GROUP);
            stats.put("auditPendingCount",
                    auditPending != null ? auditPending.getTotalPendingMessages() : 0);

        } catch (Exception e) {
            log.error("[MQ] 获取队列统计失败", e);
        }

        return stats;
    }
}
