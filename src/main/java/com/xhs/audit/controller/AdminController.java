package com.xhs.audit.controller;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 清理数据库中的无效内容
     * 扫描所有内容记录，删除不符合验证规则的数据
     */
    @PostMapping("/clean-invalid-contents")
    @Operation(summary = "清理无效内容", description = "扫描并删除数据库中不符合验证规则的无效内容记录")
    public ResponseEntity<Map<String, Object>> cleanInvalidContents() {
        log.info("[管理接口] 收到清理无效内容请求");

        try {
            Map<String, Object> result = crawlerService.cleanInvalidContents();
            log.info("[管理接口] 清理完成: {}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[管理接口] 清理失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "清理失败",
                            "message", e.getMessage()));
        }
    }

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
}
