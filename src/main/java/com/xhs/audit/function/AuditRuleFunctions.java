package com.xhs.audit.function;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import com.xhs.audit.function.AuditRuleFunctions.AuditRuleDTO;
import com.xhs.audit.model.entity.AuditRule;
import com.xhs.audit.model.entity.SensitiveWord;
import com.xhs.audit.repository.AuditRuleRepository;
import com.xhs.audit.repository.SensitiveWordRepository;

/**
 * 审核规则Function Calling工具
 * 提供给LLM Agent调用的工具集，用于获取审核规则和检查敏感词
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Component
public class AuditRuleFunctions {

    @Autowired
    private AuditRuleRepository auditRuleRepository;

    @Autowired
    private SensitiveWordRepository sensitiveWordRepository;

    /**
     * Function 1: 获取审核规则
     * LLM可调用此工具获取指定维度的审核规则
     * 
     * @return Function<GetAuditRulesRequest, List<AuditRuleDTO>>
     */
    @Bean
    @Description("从数据库获取指定维度的审核规则，用于评估内容是否合规。" +
            "参数dimension可选值: title(标题), content(内容), tag(标签), image(图片), all(全部)")
    public Function<GetAuditRulesRequest, List<AuditRuleDTO>> getAuditRules() {
        return request -> {
            String dimension = request.dimension();

            List<AuditRule> rules;
            if ("all".equalsIgnoreCase(dimension)) {
                // 获取所有启用的规则，按优先级排序
                rules = auditRuleRepository.findByEnabledTrueOrderByPriorityAsc();
            } else {
                // 获取指定维度的规则
                rules = auditRuleRepository.findByDimensionAndEnabledTrue(dimension);
            }

            log.info("获取审核规则: dimension={}, count={}", dimension, rules.size());

            // 转换为DTO（简化返回内容，减少token消耗）
            return rules.stream()
                    .map(rule -> new AuditRuleDTO(
                            rule.getId(),
                            rule.getDimension(),
                            rule.getRuleType(),
                            rule.getRuleName(),
                            rule.getRuleContent(),
                            rule.getPriority(),
                            rule.getDescription()))
                    .collect(Collectors.toList());
        };
    }

    /**
     * Function 2: 检查敏感词
     * LLM可调用此工具检查文本中是否包含敏感词
     * 
     * @return Function<CheckSensitiveWordsRequest, SensitiveWordResult>
     */
    @Bean
    @Description("检查文本中是否包含敏感词，返回匹配的敏感词列表及其严重程度。" +
            "输入文本可以是标题、内容或标签")
    public Function<CheckSensitiveWordsRequest, SensitiveWordResult> checkSensitiveWords() {
        return request -> {
            String text = request.text();

            if (text == null || text.isEmpty()) {
                return new SensitiveWordResult(false, List.of(), "NONE");
            }

            // 获取所有启用的敏感词
            List<SensitiveWord> allSensitiveWords = sensitiveWordRepository.findByEnabledTrue();

            // 简单匹配算法（生产环境建议使用AC自动机算法优化性能）
            List<SensitiveWordMatch> matches = new ArrayList<>();
            for (SensitiveWord sw : allSensitiveWords) {
                if (text.contains(sw.getWord())) {
                    matches.add(new SensitiveWordMatch(
                            sw.getWord(),
                            sw.getCategory(),
                            sw.getSeverity()));
                }
            }

            // 计算最高严重程度
            String highestSeverity = "NONE";
            if (!matches.isEmpty()) {
                highestSeverity = matches.stream()
                        .map(SensitiveWordMatch::severity)
                        .max(this::compareSeverity)
                        .orElse("LOW");
            }

            boolean found = !matches.isEmpty();
            log.info("敏感词检查: textLength={}, found={}, matchCount={}, highestSeverity={}",
                    text.length(), found, matches.size(), highestSeverity);

            return new SensitiveWordResult(found, matches, highestSeverity);
        };
    }

    /**
     * 比较严重程度
     */
    private int compareSeverity(String s1, String s2) {
        int level1 = severityToInt(s1);
        int level2 = severityToInt(s2);
        return Integer.compare(level1, level2);
    }

    /**
     * 严重程度转换为数值
     */
    private int severityToInt(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    // ==================== DTO定义 ====================

    /**
     * 获取审核规则请求
     */
    public record GetAuditRulesRequest(String dimension) {
    }

    /**
     * 审核规则DTO（简化版）
     */
    public record AuditRuleDTO(
            Long id,
            String dimension,
            String ruleType,
            String ruleName,
            String ruleContent,
            Integer priority,
            String description) {
    }

    /**
     * 检查敏感词请求
     */
    public record CheckSensitiveWordsRequest(String text) {
    }

    /**
     * 敏感词匹配结果
     */
    public record SensitiveWordMatch(
            String word,
            String category,
            String severity) {
    }

    /**
     * 敏感词检查结果
     */
    public record SensitiveWordResult(
            boolean found,
            List<SensitiveWordMatch> matches,
            String highestSeverity) {
    }
}
