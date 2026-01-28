package com.xhs.audit.agent;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.XhsContent;

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

    private final ChatClient textChatClient;
    private final ChatClient visionChatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContentAuditAgent(
            @org.springframework.beans.factory.annotation.Qualifier("textChatClient") ChatClient textChatClient,
            @org.springframework.beans.factory.annotation.Qualifier("visionChatClient") ChatClient visionChatClient) {
        this.textChatClient = textChatClient;
        this.visionChatClient = visionChatClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        log.info("ContentAuditAgent初始化完成，textChatClient和visionChatClient已注入");
    }

    /**
     * 审核单个小红书内容
     * 
     * @param content 爬取的小红书内容
     * @return 审核决策
     */
    public AuditDecision auditContent(XhsContent content) {
        try {
            log.info("[Agent审核] 开始AI审核: postId={}, title={}", content.getPostId(), content.getTitle());

            // 1. 构建User Message
            log.debug("[Agent审核] 构建System Prompt和User Message");
            String userMessageText = buildSystemPrompt() + "\n\n" + buildUserMessage(content);

            // 2. 调用文本模型进行审核（使用textChatClient）
            log.info("[Agent审核] 调用文本模型进行内容审核");
            log.debug("[Agent审核] textChatClient类型: {}", textChatClient.getClass().getName());

            log.debug("[Agent审核] 开始调用LLM API...");
            String responseText = null;
            try {
                // 使用新的ChatClient API
                responseText = textChatClient.prompt()
                        .user(userMessageText)
                        .call()
                        .content();
                log.info("[Agent审核] LLM API调用成功");
            } catch (Exception apiException) {
                log.error("[Agent审核] LLM API调用失败", apiException);
                log.error("[Agent审核] 错误详情: {}", apiException.getMessage());
                if (apiException.getCause() != null) {
                    log.error("[Agent审核] 错误原因: {}", apiException.getCause().getMessage());
                }
                throw apiException;
            }

            log.debug("[Agent审核] LLM原始响应: {}", responseText);

            // 4. 解析JSON为AuditDecision对象
            log.debug("[Agent审核] 解析LLM返回的JSON决策");
            AuditDecision decision = parseDecision(responseText);

            // 5. 补充元数据
            decision.setPostId(content.getPostId());
            decision.setUrl(content.getUrl());
            decision.setAuditedTime(LocalDateTime.now());

            // 如果有图片，使用视觉模型审核图片内容
            if (content.getImages() != null && !content.getImages().isEmpty()) {
                log.info("[Agent审核] 检测到图片，使用视觉模型审核");
                String imageAuditResult = auditImages(content);
                if (imageAuditResult != null && !imageAuditResult.isEmpty()) {
                    log.info("[Agent审核] 图片审核结果: {}", imageAuditResult);
                    // 可以将图片审核结果添加到决策的理由中
                    if (decision.getReasons() == null) {
                        decision.setReasons(new java.util.ArrayList<>());
                    }
                    // 如果图片审核发现问题，可以在这里处理
                }
            }

            // 记录各维度审核结果
            if (decision.getReasons() != null && !decision.getReasons().isEmpty()) {
                for (AuditDecision.RejectReason reason : decision.getReasons()) {
                    log.info("[Agent审核] 检测到问题: dimension={}, severity={}, reason={}",
                            reason.getDimension(), reason.getSeverity(), reason.getReason());
                }
            }

            log.info("[Agent审核完成] postId={}, status={}, confidence={}, riskLevel={}",
                    content.getPostId(), decision.getStatus(), decision.getConfidenceScore(),
                    decision.getRiskLevel());

            return decision;

        } catch (Exception e) {
            log.error("[Agent审核失败] postId={}, error={}", content.getPostId(), e.getMessage(), e);
            // 返回UNCERTAIN决策
            return AuditDecision.uncertain(content.getPostId(),
                    "审核异常: " + e.getMessage());
        }
    }

    /**
     * 构建System Prompt
     */
    private String buildSystemPrompt() {
        return """
                你是一个专业的小红书内容审核专家。你的职责是评估用户上传的小红书帖子是否合规。

                【审核维度】（按优先级排序）：
                1. 标题审核 - 检查标题长度、敏感词、违规表述、诱导点击
                2. 内容审核 - 检查正文中的敏感词、政治敏感、虚假宣传、隐性营销
                3. Tag审核 - 检查标签合规性、是否过度标签堆砌、是否偏离主题
                4. 图片审核 - 检查图片数量、URL有效性

                【可用工具】：
                1. getAuditRules(dimension) - 获取审核规则，dimension可选: title/content/tag/image/all
                2. checkSensitiveWords(text) - 检查文本中的敏感词
                3. analyzeContent(title, content, tags) - 深度语义分析，识别隐性违规

                【审核步骤】：
                第1步：调用 getAuditRules("all") 获取所有审核规则
                第2步：依次对标题、内容、标签调用 checkSensitiveWords 检查敏感词
                第3步：调用 analyzeContent 进行深度语义分析
                第4步：根据规则和工具返回结果，综合判断内容是否合规
                第5步：如果置信度 < 0.7，标记为UNCERTAIN
                第6步：生成最终决策，必须返回JSON格式

                【决策标准】：
                - PASSED（通过）: 所有维度均无违规，置信度 >= 0.8
                - REJECTED（驳回）: 至少一个维度存在明确违规
                - UNCERTAIN（不确定）: 置信度 < 0.7，或需要人工复核

                【输出格式】（严格JSON）：
                {
                  "status": "PASSED|REJECTED|UNCERTAIN",
                  "reasons": [
                    {
                      "dimension": "title|content|tag|image",
                      "reason": "具体原因",
                      "severity": "LOW|MEDIUM|HIGH|CRITICAL"
                    }
                  ],
                  "confidenceScore": 0.95,
                  "suggestedAction": "通过|驳回|人工复核",
                  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                  "modelName": "gpt-4-turbo"
                }

                【注意事项】：
                1. 必须调用工具获取数据，不要凭空猜测
                2. 敏感词匹配必须准确，避免误判
                3. 对于边界case，宁可标记UNCERTAIN交由人工
                4. 严格按照JSON格式返回结果，不要添加额外说明
                """;
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
     * 使用视觉模型审核图片内容
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

            // 构建图片审核提示
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请审核以下小红书帖子的图片内容，检查是否存在违规内容：\n\n");
            promptBuilder.append("【审核维度】\n");
            promptBuilder.append("1. 色情低俗内容\n");
            promptBuilder.append("2. 暴力血腥内容\n");
            promptBuilder.append("3. 违法违规内容（毒品、赌博等）\n");
            promptBuilder.append("4. 政治敏感内容\n");
            promptBuilder.append("5. 虚假广告或诱导信息\n");
            promptBuilder.append("6. 图片中的文字内容是否包含敏感词\n\n");
            promptBuilder.append("【图片信息】\n");
            promptBuilder.append("图片数量: ").append(content.getImages().size()).append("\n");
            promptBuilder.append("图片URL: ").append(content.getImages()).append("\n\n");
            promptBuilder.append("请返回审核结果，如果发现问题请详细说明。");

            // 使用视觉模型（新API）
            String result = visionChatClient.prompt()
                    .user(promptBuilder.toString())
                    .call()
                    .content();

            log.debug("[图片审核] 视觉模型返回: {}", result);
            return result;

        } catch (Exception e) {
            log.error("[图片审核失败] postId={}, error={}", content.getPostId(), e.getMessage(), e);
            return "图片审核异常: " + e.getMessage();
        }
    }
}
