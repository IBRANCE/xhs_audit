package com.xhs.audit.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.dto.AuditDecision.RejectReason;
import com.xhs.audit.model.entity.AuditRuleConfig;
import com.xhs.audit.model.entity.XhsContent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内容规则验证器
 * 在AI审核前进行基础规则检查，快速过滤不符合要求的内容
 * 
 * 验证规则：
 * 1. 正文文字≥25字 且 图片≥1张
 * 2. 必须包含话题标签：#东风日产；#尽兴由NI #车型名字
 * 
 * @author XHS Audit System
 * @since 2026-02-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRuleValidator {

    private final AuditRuleConfigService ruleConfigService;

    /**
     * 验证内容是否符合基础规则
     * 
     * @param content 小红书内容
     * @return 验证结果，如果规则通过返回null，否则返回驳回决策
     */
    public AuditDecision validateRules(XhsContent content) {
        log.info("[规则验证] 开始验证: postId={}, title={}", content.getPostId(), content.getTitle());

        List<RejectReason> rejectReasons = new ArrayList<>();

        AuditRuleConfig config = ruleConfigService.getConfig();

        // 规则1: 检查正文文字和图片数量
        validateContentFormat(content, rejectReasons, config);

        // 规则2: 检查必带话题标签
        validateRequiredTags(content, rejectReasons, config);

        // 如果有驳回原因，返回驳回决策
        if (!rejectReasons.isEmpty()) {
            log.info("[规则验证] 验证失败: postId={}, 原因数={}", content.getPostId(), rejectReasons.size());
            for (RejectReason reason : rejectReasons) {
                log.info("[规则验证]   - [{}] {} (severity: {})",
                        reason.getDimension(), reason.getReason(), reason.getSeverity());
            }

            return AuditDecision.builder()
                    .postId(content.getPostId())
                    .url(content.getUrl())
                    .status("REJECTED")
                    .reasons(rejectReasons)
                    .confidenceScore(1.0) // 规则验证的置信度为100%
                    .suggestedAction("驳回")
                    .riskLevel("HIGH")
                    .modelName("RuleValidator")
                    .auditedTime(java.time.LocalDateTime.now())
                    .build();
        }

        log.info("[规则验证] 验证通过: postId={}", content.getPostId());
        return null; // 规则通过，返回null
    }

    /**
     * 验证内容格式 - 规则1
     * 正文文字≥25字 且 图片≥1张
     */
    private void validateContentFormat(XhsContent content, List<RejectReason> rejectReasons, AuditRuleConfig config) {
        String textContent = content.getContent();
        List<String> images = content.getImages();

        // 检查正文字数（去除空白字符后计算）
        int textLength = textContent != null ? textContent.trim().length() : 0;
        int minTextLength = config.getMinTextLength();
        if (textLength < minTextLength) {
            String reason = String.format("正文文字不足%d字（当前: %d字）", minTextLength, textLength);
            rejectReasons.add(new RejectReason("content_format", reason, "HIGH"));
            log.warn("[规则验证] {}: postId={}", reason, content.getPostId());
        }

        // 检查图片数量
        int imageCount = images != null ? images.size() : 0;
        int minImageCount = config.getMinImageCount();
        if (imageCount < minImageCount) {
            String reason = String.format("图片数量不足%d张（当前: %d张）", minImageCount, imageCount);
            rejectReasons.add(new RejectReason("content_format", reason, "HIGH"));
            log.warn("[规则验证] {}: postId={}", reason, content.getPostId());
        }
    }

    /**
     * 验证必带话题标签 - 规则2
     * 必须包含：#东风日产；#尽兴由NI #车型名字
     */
    private void validateRequiredTags(XhsContent content, List<RejectReason> rejectReasons, AuditRuleConfig config) {
        List<String> tags = content.getTags();
        if (tags == null || tags.isEmpty()) {
            rejectReasons.add(new RejectReason("tag", "未包含任何话题标签", "CRITICAL"));
            log.warn("[规则验证] 未包含任何话题标签: postId={}", content.getPostId());
            return;
        }

        // 标准化标签（去除#号，转小写）
        List<String> normalizedTags = tags.stream()
                .map(this::normalizeTag)
                .toList();

        log.debug("[规则验证] 标签列表: {}", tags);
        log.debug("[规则验证] 标准化后: {}", normalizedTags);

        // 标准化排除标签列表（用于后续匹配）
        List<String> normalizedExcludedTags = safeList(config.getExcludedTags()).stream()
                .map(this::normalizeTag)
                .toList();

        // 检查必带标签
        List<String> missingTags = new ArrayList<>();
        for (String requiredTag : safeList(config.getRequiredTags())) {
            String normalizedRequired = normalizeTag(requiredTag);
            if (!normalizedTags.contains(normalizedRequired)) {
                missingTags.add("#" + requiredTag);
            }
        }

        if (!missingTags.isEmpty()) {
            String reason = "缺少必带话题标签: " + String.join("、", missingTags);
            rejectReasons.add(new RejectReason("tag", reason, "CRITICAL"));
            log.warn("[规则验证] {}: postId={}", reason, content.getPostId());
        }

        // 检查是否包含车型名称标签
        boolean hasCarModel = false;
        String matchedCarModel = null;
        for (String tag : normalizedTags) {
            // 跳过排除的标签（使用标准化后的列表比较）
            if (normalizedExcludedTags.contains(tag)) {
                continue;
            }

            for (String carModel : safeList(config.getCarModelNames())) {
                String normalizedCarModel = normalizeTag(carModel);
                // 使用精确匹配或以车型名开头/结尾，避免过度匹配
                if (tag.equals(normalizedCarModel) ||
                        tag.startsWith(normalizedCarModel) ||
                        tag.endsWith(normalizedCarModel)) {
                    hasCarModel = true;
                    matchedCarModel = carModel;
                    break;
                }
            }
            if (hasCarModel) {
                break;
            }
        }

        if (!hasCarModel) {
            String reason = "缺少车型名称标签（如：#日产N7、#天籁、#启辰等）";
            rejectReasons.add(new RejectReason("tag", reason, "CRITICAL"));
            log.warn("[规则验证] {}: postId={}", reason, content.getPostId());
        } else {
            log.info("[规则验证] 检测到车型标签: {}", matchedCarModel);
        }
    }

    /**
     * 标准化标签
     * 去除#号、空格，转小写，用于比较
     */
    private String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.replaceAll("[#\\s]+", "").toLowerCase();
    }

    private List<String> safeList(List<String> source) {
        return source == null ? List.of() : source;
    }
}
