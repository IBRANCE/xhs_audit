package com.xhs.audit.controller;

import static com.xhs.audit.config.RedisStreamConstants.AUDIT_GROUP;
import static com.xhs.audit.config.RedisStreamConstants.AUDIT_STREAM;
import static com.xhs.audit.config.RedisStreamConstants.CRAWL_GROUP;
import static com.xhs.audit.config.RedisStreamConstants.CRAWL_STREAM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.service.CrawlerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理API控制器
 * 提供数据清理、系统维护等管理功能
 * 
 * @author XHS Audit System
 * @since 2026-01-28
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "管理API")
public class AdminController {

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    @Qualifier("textChatClient")
    private ChatClient textChatClient;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 暂时注释掉，等待实现 cleanInvalidContents 方法
    /*
     * @PostMapping("/clean-invalid-contents")
     * 
     * @Operation(summary = "清理无效内容", description = "扫描并删除数据库中不符合验证规则的无效内容记录")
     * public ResponseEntity<Map<String, Object>> cleanInvalidContents() {
     * log.info("[管理接口] 收到清理无效内容请求");
     * 
     * try {
     * Map<String, Object> result = crawlerService.cleanInvalidContents();
     * log.info("[管理接口] 清理完成: {}", result);
     * return ResponseEntity.ok(result);
     * } catch (Exception e) {
     * log.error("[管理接口] 清理失败", e);
     * return ResponseEntity.internalServerError()
     * .body(Map.of(
     * "error", "清理失败",
     * "message", e.getMessage()));
     * }
     * }
     */

    /**
     * 测试AI模型连接
     * 发送一条简单消息到AI模型，验证连接是否正常
     */
    @PostMapping("/test-ai")
    @Operation(summary = "测试AI连接", description = "测试与AI模型的连接是否正常")
    public ResponseEntity<Map<String, Object>> testAiConnection(@RequestBody Map<String, String> request) {
        String testMessage = request.getOrDefault("message", "你好，请回复'连接成功'");
        log.info("[管理接口] 测试AI连接: {}", testMessage);

        try {
            log.info("[测试AI] 准备发送消息到AI模型");
            log.info("[测试AI] textChatClient类型: {}", textChatClient.getClass().getName());

            log.info("[测试AI] 开始调用AI模型...");
            long startTime = System.currentTimeMillis();

            // 使用新的ChatClient API
            String responseText = textChatClient.prompt()
                    .user(testMessage)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[测试AI] AI模型响应成功，耗时: {}ms", duration);
            log.info("[测试AI] AI响应内容: {}", responseText);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "AI模型连接正常",
                    "request", testMessage,
                    "response", responseText,
                    "duration", duration + "ms"));

        } catch (Exception e) {
            log.error("[测试AI] AI连接失败", e);
            log.error("[测试AI] 错误详情: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("[测试AI] 错误原因: {}", e.getCause().getMessage());
            }

            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "error",
                            "message", "AI模型连接失败",
                            "error", e.getMessage(),
                            "errorType", e.getClass().getSimpleName()));
        }
    }

    /**
     * 清理僵尸消息
     * 清理Redis Stream中无法被查询的Pending消息
     */
    @DeleteMapping("/clean-zombie-messages")
    @Operation(summary = "清理僵尸消息", description = "清理Redis Stream中无法被查询的Pending消息")
    public ResponseEntity<Map<String, Object>> cleanZombieMessages() {
        log.info("[管理接口] 收到清理僵尸消息请求");

        try {
            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> details = new ArrayList<>();

            // 清理爬虫队列
            Map<String, Object> crawlResult = cleanZombieMessagesFromStream(
                    CRAWL_STREAM,
                    CRAWL_GROUP,
                    "爬虫队列"
            );
            details.add(crawlResult);

            // 清理审核队列
            Map<String, Object> auditResult = cleanZombieMessagesFromStream(
                    AUDIT_STREAM,
                    AUDIT_GROUP,
                    "审核队列"
            );
            details.add(auditResult);

            result.put("status", "success");
            result.put("details", details);
            result.put("totalDeleted",
                    (Integer) crawlResult.getOrDefault("deleted", 0) +
                    (Integer) auditResult.getOrDefault("deleted", 0));

            log.info("[管理接口] 清理僵尸消息完成: {}", result);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[管理接口] 清理僵尸消息失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "error",
                            "message", "清理失败",
                            "error", e.getMessage()));
        }
    }

    /**
     * 清理指定Stream中的僵尸消息
     */
    private Map<String, Object> cleanZombieMessagesFromStream(String streamKey, String groupName, String queueName) {
        Map<String, Object> result = new HashMap<>();
        result.put("queue", queueName);

        try {
            // 1. 获取Pending消息总数
            PendingMessagesSummary summary = redisTemplate.opsForStream().pending(streamKey, groupName);
            long totalPending = summary != null ? summary.getTotalPendingMessages() : 0;
            result.put("pendingBefore", totalPending);

            if (totalPending == 0) {
                result.put("deleted", 0);
                result.put("status", "no_messages");
                return result;
            }

            // 2. 尝试查询详细的Pending消息
            var pendingMessages = redisTemplate.opsForStream().pending(
                    streamKey,
                    org.springframework.data.redis.connection.stream.Consumer.from(groupName, "*"),
                    org.springframework.data.domain.Range.unbounded(),
                    100L);

            int deletedCount = 0;

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                // 详细查询为空，但摘要有数量 - 这些是僵尸消息
                log.warn("[管理接口] {} 发现僵尸消息: {} 条", queueName, totalPending);

                // 尝试使用XCLAIM强制声明并删除
                try {
                    // 读取Stream中的所有消息ID
                    var messages = redisTemplate.opsForStream().range(streamKey,
                            org.springframework.data.domain.Range.unbounded(),
                            org.springframework.data.redis.connection.Limit.unlimited());
                    if (messages != null && !messages.isEmpty()) {
                        for (var message : messages) {
                            try {
                                // 尝试声明消息
                                var claimed = redisTemplate.opsForStream().claim(
                                        streamKey,
                                        groupName,
                                        "admin-cleaner",
                                        java.time.Duration.ofMinutes(5),
                                        message.getId());

                                if (claimed != null && !claimed.isEmpty()) {
                                    // 立即ACK
                                    redisTemplate.opsForStream().acknowledge(streamKey, groupName, message.getId());
                                    deletedCount++;
                                    log.info("[管理接口] 清理僵尸消息: queue={}, id={}", queueName, message.getId());
                                }
                            } catch (Exception e) {
                                log.debug("[管理接口] 声明消息失败: queue={}, id={}", queueName, message.getId(), e);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[管理接口] 强制声明消息失败: queue={}", queueName, e);
                }

                // 如果还是无法清理，尝试直接创建新的Consumer Group
                if (deletedCount == 0) {
                    log.warn("[管理接口] 尝试重置Consumer Group: queue={}", queueName);
                    try {
                        // 先删除旧的Group
                        redisTemplate.opsForStream().destroyGroup(streamKey, groupName);
                        // 重新创建Group（从$开始，不读取历史消息）
                        redisTemplate.opsForStream().createGroup(streamKey, groupName);
                        result.put("action", "reset_consumer_group");
                    } catch (Exception e) {
                        log.error("[管理接口] 重置Consumer Group失败: queue={}", queueName, e);
                    }
                }
            } else {
                // 详细查询有消息，尝试清理超时的消息
                long idleTimeThresholdMs = 5 * 60 * 1000; // 5分钟

                for (var pending : pendingMessages) {
                    if (pending.getElapsedTimeSinceLastDelivery().toMillis() > idleTimeThresholdMs) {
                        try {
                            // 声明消息
                            var claimed = redisTemplate.opsForStream().claim(
                                    streamKey,
                                    groupName,
                                    "admin-cleaner",
                                    java.time.Duration.ofMinutes(5),
                                    org.springframework.data.redis.connection.stream.RecordId.of(pending.getIdAsString()));

                            if (claimed != null && !claimed.isEmpty()) {
                                // 立即ACK
                                redisTemplate.opsForStream().acknowledge(streamKey, groupName, pending.getIdAsString());
                                deletedCount++;
                                log.info("[管理接口] 清理超时消息: queue={}, id={}, idleTime={}ms",
                                        queueName, pending.getIdAsString(),
                                        pending.getElapsedTimeSinceLastDelivery().toMillis());
                            }
                        } catch (Exception e) {
                            log.warn("[管理接口] 清理消息失败: queue={}, id={}", queueName, pending.getIdAsString(), e);
                        }
                    }
                }
            }

            // 获取清理后的Pending数量
            PendingMessagesSummary summaryAfter = redisTemplate.opsForStream().pending(streamKey, groupName);
            long pendingAfter = summaryAfter != null ? summaryAfter.getTotalPendingMessages() : 0;
            result.put("pendingAfter", pendingAfter);
            result.put("deleted", deletedCount);
            result.put("status", "success");

        } catch (Exception e) {
            log.error("[管理接口] 清理队列失败: queue={}", queueName, e);
            result.put("status", "error");
            result.put("error", e.getMessage());
        }

        return result;
    }
}
