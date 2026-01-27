package com.xhs.audit.agent;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
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

    private final OpenAiChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContentAuditAgent(OpenAiChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        log.info("ContentAuditAgent初始化完成，使用OpenAiChatClient");
    }

    /**
     * 审核单个小红书内容
     * 
     * @param content 爬取的小红书内容
     * @return 审核决策
     */
    public AuditDecision auditContent(XhsContent content) {
        try {
            log.info("开始审核内容: postId={}, url={}", content.getPostId(), content.getUrl());

            // 1. 构建User Message
            String userMessageText = buildSystemPrompt() + "\n\n" + buildUserMessage(content);
            Message userMessage = new UserMessage(userMessageText);

            // 2. 配置OpenAI选项
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .withModel("gpt-4")
                    .withTemperature(0.3f)
                    .withMaxTokens(2000)
                    .build();

            // 3. 调用LLM
            Prompt prompt = new Prompt(List.of(userMessage), options);
            ChatResponse response = chatClient.call(prompt);
            String responseText = response.getResult().getOutput().getContent();

            log.debug("LLM原始响应: {}", responseText);

            // 4. 解析JSON为AuditDecision对象
            AuditDecision decision = parseDecision(responseText);

            // 5. 补充元数据
            decision.setPostId(content.getPostId());
            decision.setUrl(content.getUrl());
            decision.setAuditedTime(LocalDateTime.now());
            if (decision.getModelName() == null) {
                decision.setModelName("gpt-4");
            }

            log.info("审核完成: postId={}, status={}, confidence={}",
                    content.getPostId(), decision.getStatus(), decision.getConfidenceScore());

            return decision;

        } catch (Exception e) {
            log.error("审核失败: postId={}, error={}", content.getPostId(), e.getMessage(), e);
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
}
