package com.xhs.audit.agent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xhs.audit.config.AuditPromptConfig;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.service.ContentRuleValidator;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * 内容审核Agent
 * 核心AI审核引擎，使用LLM + Function Calling实现智能审核
 * 
 * 工作流程:
 * 1. 接收XhsContent对象
 * 2. 构建System Prompt和User Message
 * 3. LLM调用Function Calling工具(getAuditRules, checkSensitiveWords,
 * analyzeContent)
 * 4. LLM综合分析后返回结构化AuditDecision
 * 5. 补充元数据并返回
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Component
public class ContentAuditAgent {

    // ============ 常量定义 ============

    /** 默认置信度 */
    private static final double DEFAULT_CONFIDENCE_SCORE = 0.5;

    /** LLM temperature 参数 */
    private static final double LLM_TEMPERATURE = 0.3;

    /** LLM max_tokens 参数 */
    private static final int LLM_MAX_TOKENS = 100;

    // ============ 依赖注入 ============

    private final ChatClient textChatClient;
    private final ObjectMapper objectMapper;
    private final Executor auditExecutor;
    private final String visionModel;
    private final String baseUrl;
    private final String apiKey;
    private final RestTemplate visionRestTemplate;
    private final AuditPromptConfig promptConfig;
    private final StringRedisTemplate redisTemplate;
    private final Duration imageCacheTtl;
    private final boolean chatThinkEnabled;
    private final ContentRuleValidator contentRuleValidator;
    private final int textAuditTimeoutSeconds;
    private final int imageAuditTimeoutSeconds;
    private final boolean visionAuditEnabled;

    @Autowired
    public ContentAuditAgent(
            @org.springframework.beans.factory.annotation.Qualifier("textChatClient") ChatClient textChatClient,
            @org.springframework.beans.factory.annotation.Qualifier("auditTaskExecutor") Executor auditExecutor,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.vision.model:gpt-4-vision-preview}") String visionModel,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.api-key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.think-enabled:false}") boolean chatThinkEnabled,
            AuditPromptConfig promptConfig,
            StringRedisTemplate redisTemplate,
            @Value("${audit.image-cache-ttl-hours:24}") int imageCacheTtlHours,
            @Value("${audit.llm.text-timeout-seconds:90}") int textAuditTimeoutSeconds,
            @Value("${audit.llm.image-timeout-seconds:120}") int imageAuditTimeoutSeconds,
            @Value("${audit.vision.enabled:true}") boolean visionAuditEnabled,
            ContentRuleValidator contentRuleValidator) {
        this.textChatClient = textChatClient;
        this.auditExecutor = auditExecutor;
        this.visionModel = visionModel;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatThinkEnabled = chatThinkEnabled;
        this.promptConfig = promptConfig;
        this.redisTemplate = redisTemplate;
        this.imageCacheTtl = Duration.ofHours(imageCacheTtlHours);
        this.contentRuleValidator = contentRuleValidator;
        this.textAuditTimeoutSeconds = textAuditTimeoutSeconds;
        this.imageAuditTimeoutSeconds = imageAuditTimeoutSeconds;
        this.visionAuditEnabled = visionAuditEnabled;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        this.visionRestTemplate = new RestTemplate();
        org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        visionRestTemplate.setRequestFactory(factory);

        log.info("ContentAuditAgent初始化完成，视觉模型: {}, Base URL: {}", visionModel, baseUrl);
        log.info("图片审核开关: {}", visionAuditEnabled ? "开启" : "关闭");
        log.info("图片缓存过期时间: {}小时", imageCacheTtlHours);
        log.info("文本模型思考模式: {}", chatThinkEnabled ? "开启" : "关闭");
        log.info("审核超时配置 - 文本: {}秒, 图片: {}秒", textAuditTimeoutSeconds, imageAuditTimeoutSeconds);
    }

    /**
     * 审核单个小红书内容 - 并行处理文本和图片
     * v4.0: 添加断路器和重试保护
     * v4.1: 添加基础规则预验证，快速过滤不符合要求的内容
     *
     * @param content 爬取的小红书内容
     * @return 审核决策
     */
    @CircuitBreaker(name = "llmService", fallbackMethod = "auditContentFallback")
    @Retry(name = "llmService")
    public AuditDecision auditContent(XhsContent content) {
        try {
            log.info("[Agent审核] 开始审核: postId={}, title={}", content.getPostId(), content.getTitle());

            // 第一步：基础规则预验证（快速失败）
            AuditDecision ruleResult = contentRuleValidator.validateRules(content);
            if (ruleResult != null) {
                log.info("[Agent审核] 基础规则验证失败，快速驳回: postId={}, reasons={}",
                        content.getPostId(), ruleResult.getReasons().size());
                return ruleResult;
            }

            log.info("[Agent审核] 基础规则验证通过，继续LLM审核: postId={}", content.getPostId());

            // 第二步：并行执行文本审核和图片审核（如果启用）
            long startTime = System.currentTimeMillis();
            CompletableFuture<String> textFuture = CompletableFuture.supplyAsync(
                    () -> auditTextContent(content), auditExecutor);

            CompletableFuture<String> imageFuture;
            if (visionAuditEnabled) {
                log.info("[Agent审核] 图片审核已启用，并行执行文本和图片审核...");
                imageFuture = CompletableFuture.supplyAsync(
                        () -> auditImages(content), auditExecutor);
            } else {
                log.info("[Agent审核] 图片审核已禁用，仅执行文本审核");
                imageFuture = CompletableFuture.completedFuture(null);
            }

            // 等待任务完成（使用独立的超时配置）
            if (visionAuditEnabled) {
                log.info("[Agent审核] 等待文本和图片审核完成 (文本超时:{}s, 图片超时:{}s)",
                        textAuditTimeoutSeconds, imageAuditTimeoutSeconds);
            } else {
                log.info("[Agent审核] 等待文本审核完成 (超时:{}s)", textAuditTimeoutSeconds);
            }

            long textStartTime = System.currentTimeMillis();
            String textResult = textFuture.get(textAuditTimeoutSeconds, TimeUnit.SECONDS);
            long textDuration = System.currentTimeMillis() - textStartTime;
            log.info("[Agent审核] 文本审核完成，耗时: {}ms", textDuration);

            String imageResult = null;
            if (visionAuditEnabled) {
                long imageStartTime = System.currentTimeMillis();
                imageResult = imageFuture.get(imageAuditTimeoutSeconds, TimeUnit.SECONDS);
                long imageDuration = System.currentTimeMillis() - imageStartTime;
                log.info("[Agent审核] 图片审核完成，耗时: {}ms", imageDuration);
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("[Agent审核] 审核总耗时: {}ms", totalDuration);

            // 构建决策
            AuditDecision decision = buildDecisionFromResults(content, textResult, imageResult);

            log.info("[Agent审核完成] postId={}, status={}, confidence={}, riskLevel={}",
                    content.getPostId(), decision.getStatus(), decision.getConfidenceScore(),
                    decision.getRiskLevel());

            return decision;

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("[Agent审核超时] postId={}, timeout={}s, 建议检查LLM服务响应速度",
                    content.getPostId(),
                    Math.max(textAuditTimeoutSeconds, imageAuditTimeoutSeconds));
            return AuditDecision.uncertain(content.getPostId(),
                    "审核超时，请稍后重试");
        } catch (Exception e) {
            log.error("[Agent审核失败] postId={}, error={}", content.getPostId(), e.getMessage(), e);
            return AuditDecision.uncertain(content.getPostId(),
                    "审核异常: " + e.getMessage());
        }
    }

    /**
     * 文本内容审核
     */
    private String auditTextContent(XhsContent content) {
        long startTime = System.currentTimeMillis();
        try {
            // 使用配置的 System Prompt
            String systemPrompt = promptConfig.getTextSystem();
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                log.error("[Agent审核] 文本审核 System Prompt 未配置");
                throw new RuntimeException("文本审核 System Prompt 未配置");
            }

            // 构建用户消息
            String userMessageText = buildUserMessage(content);

            // 如果思考模式关闭，添加 /no_think 后缀
            if (!chatThinkEnabled) {
                userMessageText = userMessageText + " /no_think";
                log.debug("[Agent审核] 思考模式关闭，添加 /no_think 后缀");
            }

            log.info("[文本审核] 开始执行文本审核: postId={}", content.getPostId());
            long llmStartTime = System.currentTimeMillis();

            // 正确使用 system() 和 user() 方法
            String responseText = textChatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessageText)
                    .call()
                    .content();

            long llmDuration = System.currentTimeMillis() - llmStartTime;
            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("[文本审核] 完成: postId={}, LLM耗时={}ms, 总耗时={}ms",
                    content.getPostId(), llmDuration, totalDuration);
            return responseText;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[文本审核] 失败: postId={}, 耗时={}ms, error={}",
                    content.getPostId(), duration, e.getMessage());

            // 使用 ObjectMapper 构建JSON，避免转义问题
            try {
                AuditDecision errorDecision = AuditDecision.builder()
                        .status("UNCERTAIN")
                        .reasons(List.of(new AuditDecision.RejectReason(
                                "text",
                                "文本审核异常: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                                "MEDIUM")))
                        .confidenceScore(0.0)
                        .build();
                return objectMapper.writeValueAsString(errorDecision);
            } catch (JsonProcessingException jsonEx) {
                log.error("[文本审核] 构建错误响应失败", jsonEx);
                return "{\"status\":\"UNCERTAIN\",\"reasons\":[]}";
            }
        }
    }

    /**
     * 根据并行结果构建决策
     */
    private AuditDecision buildDecisionFromResults(XhsContent content, String textResult, String imageResult) {
        log.info("[Agent审核] 文本审核结果: {}", textResult);

        AuditDecision decision = parseDecision(textResult);
        decision.setPostId(content.getPostId());
        decision.setUrl(content.getUrl());
        decision.setAuditedTime(LocalDateTime.now());

        // 合并图片审核结果
        if (imageResult != null && !imageResult.contains("无有效图片") && !imageResult.contains("异常")) {
            log.info("[Agent审核] 图片审核结果: {}", imageResult);

            try {
                // 尝试解析JSON格式的图片审核结果
                String cleanImageJson = extractJson(imageResult);
                AuditDecision imageDecision = objectMapper.readValue(cleanImageJson, AuditDecision.class);

                // 如果图片审核不通过，合并结果
                if (AuditDecision.Status.REJECTED.toString().equals(imageDecision.getStatus())) {
                    log.warn("[Agent审核] 图片审核不通过，修改最终状态为REJECTED");
                    decision.setStatus(AuditDecision.Status.REJECTED.toString());

                    // 合并图片审核的原因
                    if (decision.getReasons() == null) {
                        decision.setReasons(new java.util.ArrayList<>());
                    }
                    if (imageDecision.getReasons() != null) {
                        decision.getReasons().addAll(imageDecision.getReasons());
                    }

                    // 更新风险等级（取最高值）
                    if (imageDecision.getRiskLevel() != null) {
                        if (decision.getRiskLevel() == null ||
                                imageDecision.getRiskLevel().compareTo(decision.getRiskLevel()) > 0) {
                            decision.setRiskLevel(imageDecision.getRiskLevel());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[Agent审核] 图片审核结果解析失败，采用保守策略驳回: {}", e.getMessage());
                // 解析失败时采用保守策略：标记为驳回
                decision.setStatus(AuditDecision.Status.REJECTED.toString());
                if (decision.getReasons() == null) {
                    decision.setReasons(new java.util.ArrayList<>());
                }
                decision.getReasons().add(new AuditDecision.RejectReason(
                        "image_parse_error",
                        "图片审核结果格式异常，出于安全考虑驳回",
                        "HIGH"));
            }
        } else {
            log.info("[Agent审核] 图片审核结果: 无有效图片或异常");
        }

        // 打印汇总后的审核结果
        log.info("[Agent审核] ===== 最终审核结果 =====");
        log.info("[Agent审核] postId: {}", content.getPostId());
        log.info("[Agent审核] 状态: {}", decision.getStatus());
        log.info("[Agent审核] 置信度: {}", decision.getConfidenceScore());
        log.info("[Agent审核] 风险等级: {}", decision.getRiskLevel());
        log.info("[Agent审核] 建议操作: {}", decision.getSuggestedAction());
        if (decision.getReasons() != null && !decision.getReasons().isEmpty()) {
            log.info("[Agent审核] 驳回原因:");
            for (AuditDecision.RejectReason reason : decision.getReasons()) {
                log.info("[Agent审核]   - [{}] {} (severity: {})", reason.getDimension(), reason.getReason(),
                        reason.getSeverity());
            }
        }
        log.info("[Agent审核] ========================");

        return decision;
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(XhsContent content) {
        // 格式化标签显示
        String tagsDisplay = "无标签";
        if (content.getTags() != null && !content.getTags().isEmpty()) {
            tagsDisplay = String.join("、", content.getTags());
        }

        // 正确计算图片数量
        int imageCount = (content.getImages() != null) ? content.getImages().size() : 0;

        return String.format("""
                请审核以下小红书帖子：

                【帖子ID】%s
                【标题】%s
                【内容】%s
                【标签】%s
                【图片数量】%d
                【发布者ID】%s
                【发布时间】%s

                请严格按照System Prompt中的步骤进行审核，返回JSON格式决策。
                """,
                content.getPostId(),
                content.getTitle() != null ? content.getTitle() : "无标题",
                content.getContent() != null ? content.getContent() : "无内容",
                tagsDisplay,
                imageCount,
                content.getAuthorId() != null ? content.getAuthorId() : "未知",
                content.getPublishedAt() != null ? content.getPublishedAt().toString() : "未知");
    }

    /**
     * 解析LLM返回的JSON为AuditDecision对象
     * v5.1: 改进错误处理，避免解析失败导致整个审核崩溃
     */
    private AuditDecision parseDecision(String jsonText) {
        try {
            // 提取JSON（去除Markdown代码块标记）
            String cleanJson = extractJson(jsonText);

            // 解析为AuditDecision
            AuditDecision decision = objectMapper.readValue(cleanJson, AuditDecision.class);

            // 验证必填字段
            if (decision.getStatus() == null) {
                throw new IllegalArgumentException("缺少status字段");
            }

            // 设置默认值
            if (decision.getReasons() == null) {
                decision.setReasons(List.of());
            }
            if (decision.getConfidenceScore() == null) {
                decision.setConfidenceScore(DEFAULT_CONFIDENCE_SCORE);
            }

            return decision;

        } catch (Exception e) {
            log.error("[文本审核] JSON解析失败，返回保守决策: {}", jsonText, e);
            // 返回保守的驳回决策，而不是抛出异常
            return AuditDecision.builder()
                    .status("REJECTED")
                    .reasons(List.of(new AuditDecision.RejectReason(
                            "parse_error",
                            "LLM返回格式错误，出于安全考虑驳回: " + e.getMessage(),
                            "HIGH")))
                    .confidenceScore(0.0)
                    .suggestedAction("人工复审")
                    .riskLevel("HIGH")
                    .modelName("ParseErrorFallback")
                    .build();
        }
    }

    /**
     * 从文本中提取JSON
     * 处理LLM可能返回的Markdown格式（```json ... ```）
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return "{}";
        }

        // 去除Markdown代码块
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }

        // 提取第一个JSON对象
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return text.trim();
    }

    /**
     * 使用视觉模型审核图片内容 - 直接使用 RestTemplate 调用
     * v5.0: 支持多图审核，返回JSON格式结果
     *
     * @param content 包含图片的内容
     * @return 图片审核结果描述（JSON格式）
     */
    private String auditImages(XhsContent content) {
        long startTime = System.currentTimeMillis();
        try {
            if (content.getImages() == null || content.getImages().isEmpty()) {
                return null;
            }

            log.info("[图片审核] 开始审核图片: postId={}, imageCount={}",
                    content.getPostId(), content.getImages().size());

            // 使用配置的提示词，要求返回JSON格式
            String textPrompt = promptConfig.getTextImage();
            if (textPrompt == null || textPrompt.isEmpty()) {
                textPrompt = "请识别这张图片中是否是日产系车型";
                log.warn("[图片审核] 提示词未配置，使用默认提示词");
            }

            // 添加JSON格式要求
            textPrompt += "\n\n请严格按照以下JSON格式返回审核结果：\n" +
                    "{\"status\":\"APPROVED/REJECTED\",\"reasons\":[{\"dimension\":\"image\",\"reason\":\"具体原因\",\"severity\":\"HIGH/MEDIUM/LOW\"}],\"riskLevel\":\"HIGH/MEDIUM/LOW\"}";

            // 处理多张图片（最多审核前3张）
            int maxImages = Math.min(content.getImages().size(), 3);
            List<String> imageResults = new ArrayList<>();

            for (int i = 0; i < maxImages; i++) {
                String imageUrl = content.getImages().get(i);
                log.info("[图片审核] 处理第 {}/{} 张图片: {}", i + 1, maxImages, imageUrl);

                // 下载并压缩图片
                long downloadStartTime = System.currentTimeMillis();
                byte[] compressedBytes = downloadAndCompressImage(imageUrl);
                long downloadDuration = System.currentTimeMillis() - downloadStartTime;
                log.info("[图片审核] 第{}张图片下载+压缩耗时: {}ms", i + 1, downloadDuration);

                if (compressedBytes == null) {
                    log.warn("[图片审核] 第{}张图片下载失败，跳过", i + 1);
                    continue;
                }

                String mimeType = detectMimeType(compressedBytes);
                String base64Data = Base64.getEncoder().encodeToString(compressedBytes);
                String dataUrl = "data:" + mimeType + ";base64," + base64Data;
                log.info("[图片审核] 第{}张图片大小: {} bytes, base64长度: {}", i + 1, compressedBytes.length, base64Data.length());

                // 构建 OpenAI 格式的请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", visionModel);

                // 构建消息内容（数组格式）
                List<Map<String, Object>> contentList = new ArrayList<>();

                // 文本内容
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", textPrompt);
                contentList.add(textContent);

                // 图片内容
                Map<String, Object> imageContent = new HashMap<>();
                imageContent.put("type", "image_url");
                Map<String, Object> imageUrlObj = new HashMap<>();
                imageUrlObj.put("url", dataUrl);
                imageContent.put("image_url", imageUrlObj);
                contentList.add(imageContent);

                // 构建消息
                List<Map<String, Object>> messages = new ArrayList<>();
                Map<String, Object> message = new HashMap<>();
                message.put("role", "user");
                message.put("content", contentList);
                messages.add(message);

                requestBody.put("messages", messages);
                requestBody.put("temperature", LLM_TEMPERATURE);
                requestBody.put("max_tokens", LLM_MAX_TOKENS);

                // 构建请求URL（确保末尾有 /v1）
                String apiUrl = baseUrl;
                if (!apiUrl.endsWith("/v1")) {
                    apiUrl = apiUrl + "/v1";
                }
                apiUrl = apiUrl + "/chat/completions";

                log.info("[图片审核] 调用API: {}", apiUrl);

                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                // 发送请求
                long visionApiStartTime = System.currentTimeMillis();
                ResponseEntity<String> response = visionRestTemplate.exchange(
                        apiUrl,
                        HttpMethod.POST,
                        entity,
                        String.class);
                long visionApiDuration = System.currentTimeMillis() - visionApiStartTime;
                log.info("[图片审核] 第{}张图片 Vision API调用耗时: {}ms", i + 1, visionApiDuration);

                // 解析响应
                String result = "";
                if (response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode choices = root.path("choices");
                    if (choices.isArray() && !choices.isEmpty()) {
                        JsonNode choice = choices.get(0);
                        JsonNode messageNode = choice.path("message");
                        result = messageNode.path("content").asText();
                    }
                }

                log.info("[图片审核] 第{}张图片视觉模型返回: {}", i + 1, result);
                imageResults.add(result);
            }

            // 合并多图审核结果
            if (imageResults.isEmpty()) {
                return "无有效图片";
            }

            // 如果任意一张图片不通过，则整体不通过
            for (String result : imageResults) {
                try {
                    String cleanJson = extractJson(result);
                    AuditDecision imgDecision = objectMapper.readValue(cleanJson, AuditDecision.class);
                    if (AuditDecision.Status.REJECTED.toString().equals(imgDecision.getStatus())) {
                        log.warn("[图片审核] 检测到不合规图片，返回REJECTED状态");
                        long totalDuration = System.currentTimeMillis() - startTime;
                        log.info("[图片审核] 完成: postId={}, 总耗时={}ms", content.getPostId(), totalDuration);
                        return result; // 返回第一个不通过的结果
                    }
                } catch (Exception e) {
                    log.warn("[图片审核] 结果解析失败: {}", e.getMessage());
                }
            }

            // 所有图片都通过，返回第一个结果
            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("[图片审核] 完成: postId={}, 总耗时={}ms, 所有图片通过审核", content.getPostId(), totalDuration);
            return imageResults.get(0);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[图片审核失败] postId={}, 耗时={}ms, error={}",
                    content.getPostId(), duration, e.getMessage(), e);
            return "图片审核异常: " + e.getMessage();
        }
    }

    /**
     * 下载并压缩图片（带Redis缓存）
     * v5.0: 使用SHA256作为缓存key，避免hash冲突
     * 
     * @param imageUrl 图片URL
     * @return 压缩后的图片字节数组
     */
    private byte[] downloadAndCompressImage(String imageUrl) {
        try {
            // 1. 先检查Redis缓存（使用SHA256避免冲突）
            String cacheKey = "img:base64:" + sha256(imageUrl);
            String cachedBase64 = redisTemplate.opsForValue().get(cacheKey);

            if (cachedBase64 != null && !cachedBase64.isEmpty()) {
                log.info("[图片审核] 缓存命中: {}, 长度: {}", imageUrl, cachedBase64.length());
                byte[] cachedBytes = Base64.getDecoder().decode(cachedBase64);
                log.info("[图片审核] 使用缓存图片: {} bytes", cachedBytes.length);
                return cachedBytes;
            }

            log.info("[图片审核] 缓存未命中，下载图片: {}", imageUrl);

            // 2. 下载图片
            RestTemplate restTemplate = new RestTemplate();
            byte[] originalBytes = restTemplate.getForObject(imageUrl, byte[].class);

            if (originalBytes == null || originalBytes.length == 0) {
                return null;
            }

            log.info("[图片审核] 原始图片大小: {} bytes", originalBytes.length);

            // 3. 压缩图片
            byte[] compressedBytes = compressImage(originalBytes);
            if (compressedBytes == null) {
                return null;
            }

            // 4. 存入Redis缓存
            String base64Data = Base64.getEncoder().encodeToString(compressedBytes);
            try {
                redisTemplate.opsForValue().set(cacheKey, base64Data, imageCacheTtl);
                log.info("[图片审核] 图片已缓存: {}, 长度: {}", cacheKey, base64Data.length());
            } catch (Exception e) {
                log.warn("[图片审核] 缓存写入失败: {}", e.getMessage());
            }

            return compressedBytes;

        } catch (Exception e) {
            log.warn("[图片审核] 图片处理失败: {}, error: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 压缩图片到指定尺寸
     * v5.0: 提高压缩质量到512px，保留更多细节供视觉模型分析
     */
    private byte[] compressImage(byte[] originalBytes) {
        try {
            BufferedImage originalImage = ImageIO.read(new java.io.ByteArrayInputStream(originalBytes));
            if (originalImage == null) {
                return originalBytes;
            }

            // 压缩到最大 512 像素（提高质量）
            int maxDimension = 512;
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (width > maxDimension || height > maxDimension) {
                double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
                width = (int) (width * scale);
                height = (int) (height * scale);
            }

            BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            resizedImage.getGraphics()
                    .drawImage(originalImage.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(resizedImage, "jpg", outputStream);

            log.info("[图片审核] 压缩: {}x{} -> {} bytes", width, height, outputStream.size());
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.warn("[图片审核] 图片压缩失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * detectMimeType - 检测图片MIME类型
     */
    private String detectMimeType(byte[] bytes) {
        if (bytes.length < 4) {
            return "image/jpeg";
        }

        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        } else if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50
                && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
            return "image/png";
        } else if (bytes[0] == (byte) 0x47 && bytes[1] == (byte) 0x49
                && bytes[2] == (byte) 0x46) {
            return "image/gif";
        } else if (bytes[0] == (byte) 0x52 && bytes[1] == (byte) 0x49
                && bytes[2] == (byte) 0x46 && bytes[3] == (byte) 0x46) {
            return "image/webp";
        }

        return "image/jpeg";
    }

    /**
     * 计算字符串的SHA256哈希值（用于缓存key）
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("[工具方法] SHA256计算失败，使用hashCode: {}", e.getMessage());
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * v4.0: 断路器 Fallback 方法
     * 当 LLM 服务不可用时返回 UNCERTAIN 状态
     */
    private AuditDecision auditContentFallback(XhsContent content, Throwable t) {
        log.error("[Agent审核降级] postId={}, error={}", content.getPostId(), t.getMessage());
        return AuditDecision.uncertain(content.getPostId(),
                "LLM服务暂时不可用，请稍后重试: " + t.getMessage());
    }
}
