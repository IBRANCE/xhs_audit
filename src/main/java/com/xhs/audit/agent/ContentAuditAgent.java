package com.xhs.audit.agent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import org.springframework.beans.factory.annotation.Qualifier;
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

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

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

    @Autowired
    public ContentAuditAgent(
            @org.springframework.beans.factory.annotation.Qualifier("textChatClient") ChatClient textChatClient,
            @org.springframework.beans.factory.annotation.Qualifier("auditTaskExecutor") Executor auditExecutor,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.vision.model:gpt-4-vision-preview}") String visionModel,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.api-key}") String apiKey,
            AuditPromptConfig promptConfig,
            StringRedisTemplate redisTemplate,
            @Value("${audit.image-cache-ttl-hours:24}") int imageCacheTtlHours) {
        this.textChatClient = textChatClient;
        this.auditExecutor = auditExecutor;
        this.visionModel = visionModel;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.promptConfig = promptConfig;
        this.redisTemplate = redisTemplate;
        this.imageCacheTtl = Duration.ofHours(imageCacheTtlHours);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        this.visionRestTemplate = new RestTemplate();
        org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory =
            new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        visionRestTemplate.setRequestFactory(factory);

        log.info("ContentAuditAgent初始化完成，视觉模型: {}, Base URL: {}", visionModel, baseUrl);
        log.info("图片缓存过期时间: {}小时", imageCacheTtlHours);
    }

    /**
     * 审核单个小红书内容 - 并行处理文本和图片
     *
     * @param content 爬取的小红书内容
     * @return 审核决策
     */
    public AuditDecision auditContent(XhsContent content) {
        try {
            log.info("[Agent审核] 开始AI审核: postId={}, title={}", content.getPostId(), content.getTitle());

            // 并行执行文本审核和图片审核
            CompletableFuture<String> textFuture = CompletableFuture.supplyAsync(
                    () -> auditTextContent(content), auditExecutor);
            CompletableFuture<String> imageFuture = CompletableFuture.supplyAsync(
                    () -> auditImages(content), auditExecutor);

            // 等待两个任务完成
            log.info("[Agent审核] 并行执行文本审核和图片审核...");
            String textResult = textFuture.get(60, TimeUnit.SECONDS);
            String imageResult = imageFuture.get(60, TimeUnit.SECONDS);

            // 构建决策
            AuditDecision decision = buildDecisionFromResults(content, textResult, imageResult);

            log.info("[Agent审核完成] postId={}, status={}, confidence={}, riskLevel={}",
                    content.getPostId(), decision.getStatus(), decision.getConfidenceScore(),
                    decision.getRiskLevel());

            return decision;

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
        try {
            // 使用配置的 System Prompt
            String systemPrompt = promptConfig.getTextSystem();
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                log.error("[Agent审核] 文本审核 System Prompt 未配置");
                throw new RuntimeException("文本审核 System Prompt 未配置");
            }

            String userMessageText = systemPrompt + "\n\n" + buildUserMessage(content);
            log.info("[Agent审核] 执行文本审核");

            String responseText = textChatClient.prompt()
                    .user(userMessageText)
                    .call()
                    .content();

            log.info("[Agent审核] 文本审核完成");
            return responseText;
        } catch (Exception e) {
            log.error("[Agent审核] 文本审核失败: {}", e.getMessage());
            return "{\"status\":\"UNCERTAIN\",\"reasons\":[{\"dimension\":\"text\",\"reason\":\"" + e.getMessage() + "\",\"severity\":\"MEDIUM\"}]}";
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

            // 使用配置的关键词判断图片是否通过
            String passKeywords = promptConfig.getImagePassKeywords();
            if (passKeywords == null || passKeywords.isEmpty()) {
                passKeywords = "是";
            }

            // 如果图片审核结果不包含通过关键词，添加到原因中
            if (!imageResult.contains(passKeywords)) {
                if (decision.getReasons() == null) {
                    decision.setReasons(new java.util.ArrayList<>());
                }
                decision.getReasons().add(new AuditDecision.RejectReason("image", "图片不符合要求: " + imageResult, "MEDIUM"));
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
                log.info("[Agent审核]   - [{}] {} (severity: {})", reason.getDimension(), reason.getReason(), reason.getSeverity());
            }
        }
        log.info("[Agent审核] ========================");

        return decision;
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(XhsContent content) {
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
                content.getTags() != null ? content.getTags() : "[]",
                content.getImages() != null ? content.getImages().toString().split(",").length : 0,
                content.getAuthorId() != null ? content.getAuthorId() : "未知",
                content.getPublishedAt() != null ? content.getPublishedAt().toString() : "未知");
    }

    /**
     * 解析LLM返回的JSON为AuditDecision对象
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
                decision.setConfidenceScore(0.5);
            }

            return decision;

        } catch (JsonProcessingException e) {
            log.error("JSON解析失败: {}", jsonText, e);
            throw new RuntimeException("LLM返回格式错误，无法解析JSON", e);
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
     *
     * @param content 包含图片的内容
     * @return 图片审核结果描述
     */
    private String auditImages(XhsContent content) {
        try {
            if (content.getImages() == null || content.getImages().isEmpty()) {
                return null;
            }

            log.info("[图片审核] 开始审核图片: postId={}, imageCount={}",
                    content.getPostId(), content.getImages().size());

            // 使用配置的提示词
            String textPrompt = promptConfig.getTextImage();
            if (textPrompt == null || textPrompt.isEmpty()) {
                textPrompt = "请识别这张图片中是否是日产系车型，回复：是/否";
                log.warn("[图片审核] 提示词未配置，使用默认提示词");
            }

            // 只处理第一张图片
            String imageUrl = content.getImages().get(0);
            log.info("[图片审核] 处理图片: {}", imageUrl);

            // 下载并压缩图片
            byte[] compressedBytes = downloadAndCompressImage(imageUrl);
            if (compressedBytes == null) {
                return "图片下载失败";
            }

            String mimeType = detectMimeType(compressedBytes);
            String base64Data = Base64.getEncoder().encodeToString(compressedBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Data;
            log.info("[图片审核] 图片大小: {} bytes, base64长度: {}", compressedBytes.length, base64Data.length());
            log.debug("[图片审核] Base64数据: {}", base64Data);

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
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 100);

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
            ResponseEntity<String> response = visionRestTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

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

            log.info("[图片审核] 视觉模型返回: {}", result);
            return result;

        } catch (Exception e) {
            log.error("[图片审核失败] postId={}, error={}", content.getPostId(), e.getMessage(), e);
            return "图片审核异常: " + e.getMessage();
        }
    }

    /**
     * 下载并压缩图片（带Redis缓存）
     * @param imageUrl 图片URL
     * @return 压缩后的图片字节数组
     */
    private byte[] downloadAndCompressImage(String imageUrl) {
        try {
            // 1. 先检查Redis缓存
            String cacheKey = "img:base64:" + Integer.toHexString(imageUrl.hashCode());
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
     */
    private byte[] compressImage(byte[] originalBytes) {
        try {
            BufferedImage originalImage = ImageIO.read(new java.io.ByteArrayInputStream(originalBytes));
            if (originalImage == null) {
                return originalBytes;
            }

            // 压缩到最大 128 像素
            int maxDimension = 128;
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (width > maxDimension || height > maxDimension) {
                double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
                width = (int) (width * scale);
                height = (int) (height * scale);
            }

            BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            resizedImage.getGraphics().drawImage(originalImage.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);

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
     * 检测图片的MIME类型
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
}
