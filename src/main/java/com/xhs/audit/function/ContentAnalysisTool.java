package com.xhs.audit.function;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 内容分析Function Calling工具
 * 提供深度内容分析能力，识别隐性违规、情感倾向等
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Component
public class ContentAnalysisTool {

    // 营销关键词模式
    private static final Pattern MARKETING_PATTERN = Pattern.compile(
            "微信|WX|VX|加我|私信|代理|招商|赚钱|暴富|月入|日赚",
            Pattern.CASE_INSENSITIVE);

    // 虚假宣传关键词模式
    private static final Pattern FALSE_AD_PATTERN = Pattern.compile(
            "100%|绝对|一定|保证|效果显著|立竿见影|包治|根治",
            Pattern.CASE_INSENSITIVE);

    // 诱导点击关键词模式
    private static final Pattern CLICKBAIT_PATTERN = Pattern.compile(
            "震惊|惊呆|不看后悔|必看|速看|火爆|疯传",
            Pattern.CASE_INSENSITIVE);

    /**
     * Function 3: 内容深度分析
     * 识别隐性违规、情感倾向、主题分类等
     * 
     * @return Function<AnalyzeContentRequest, ContentAnalysisResult>
     */
    @Bean
    @Description("对内容进行深度语义分析，识别隐含违规（隐性营销、虚假宣传、诱导点击）、" +
            "情感倾向和风险评分。输入标题、内容文本和标签列表")
    public Function<AnalyzeContentRequest, ContentAnalysisResult> analyzeContent() {
        return request -> {
            String title = request.title() != null ? request.title() : "";
            String content = request.content() != null ? request.content() : "";
            List<String> tags = request.tags() != null ? request.tags() : List.of();

            // 合并所有文本进行分析
            String fullText = title + " " + content + " " + String.join(" ", tags);

            // 1. 检测隐性营销
            boolean hasImplicitMarketing = MARKETING_PATTERN.matcher(fullText).find();

            // 2. 检测虚假宣传
            boolean hasFalseAdvertising = FALSE_AD_PATTERN.matcher(fullText).find();

            // 3. 检测诱导点击
            boolean hasClickbait = CLICKBAIT_PATTERN.matcher(fullText).find();

            // 4. 情感分析（简化实现）
            String sentiment = analyzeSentiment(fullText);

            // 5. 主题分类（简化实现）
            List<String> topics = extractTopics(tags, title);

            // 6. 计算风险分数 (0-100)
            int riskScore = calculateRiskScore(
                    hasImplicitMarketing,
                    hasFalseAdvertising,
                    hasClickbait);

            // 7. 生成详细说明
            String details = buildDetails(
                    hasImplicitMarketing,
                    hasFalseAdvertising,
                    hasClickbait,
                    sentiment,
                    riskScore);

            log.info("内容分析完成: title='{}', riskScore={}, sentiment={}",
                    truncate(title, 30), riskScore, sentiment);

            return new ContentAnalysisResult(
                    hasImplicitMarketing,
                    hasFalseAdvertising,
                    hasClickbait,
                    sentiment,
                    topics,
                    riskScore,
                    details);
        };
    }

    /**
     * 情感分析（简化实现）
     */
    private String analyzeSentiment(String text) {
        // 正面词汇
        String[] positiveWords = { "好", "棒", "赞", "美", "喜欢", "推荐", "优秀", "完美" };
        // 负面词汇
        String[] negativeWords = { "差", "烂", "坑", "假", "骗", "垃圾", "失望", "后悔" };

        int positiveCount = countMatches(text, positiveWords);
        int negativeCount = countMatches(text, negativeWords);

        if (positiveCount > negativeCount) {
            return "positive";
        } else if (negativeCount > positiveCount) {
            return "negative";
        } else {
            return "neutral";
        }
    }

    /**
     * 统计词汇出现次数
     */
    private int countMatches(String text, String[] words) {
        int count = 0;
        for (String word : words) {
            if (text.contains(word)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 提取主题（从标签和标题）
     */
    private List<String> extractTopics(List<String> tags, String title) {
        // 优先使用标签作为主题
        if (!tags.isEmpty()) {
            return tags.stream().limit(5).toList();
        }

        // 如果无标签，从标题提取关键词
        if (title != null && !title.isEmpty()) {
            return List.of(title.substring(0, Math.min(10, title.length())));
        }

        return List.of("未分类");
    }

    /**
     * 计算风险分数
     */
    private int calculateRiskScore(boolean marketing, boolean falseAd, boolean clickbait) {
        int score = 0;
        if (marketing)
            score += 40;
        if (falseAd)
            score += 40;
        if (clickbait)
            score += 20;
        return Math.min(score, 100);
    }

    /**
     * 构建详细说明
     */
    private String buildDetails(boolean marketing, boolean falseAd,
            boolean clickbait, String sentiment, int riskScore) {
        StringBuilder details = new StringBuilder();

        if (marketing) {
            details.append("检测到隐性营销特征；");
        }
        if (falseAd) {
            details.append("检测到虚假宣传特征；");
        }
        if (clickbait) {
            details.append("检测到诱导点击特征；");
        }

        details.append("情感倾向: ").append(sentiment).append("；");
        details.append("综合风险分数: ").append(riskScore);

        if (riskScore > 60) {
            details.append("（高风险）");
        } else if (riskScore > 30) {
            details.append("（中风险）");
        } else {
            details.append("（低风险）");
        }

        return details.toString();
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    // ==================== DTO定义 ====================

    /**
     * 内容分析请求
     */
    public record AnalyzeContentRequest(
            String title,
            String content,
            List<String> tags) {
    }

    /**
     * 内容分析结果
     */
    public record ContentAnalysisResult(
            boolean hasImplicitMarketing,
            boolean hasFalseAdvertising,
            boolean hasClickbait,
            String sentiment,
            List<String> topics,
            int riskScore,
            String details) {
    }
}
