package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核决策对象
 * Agent返回的结构化审核结果
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditDecision {

    /**
     * 帖子ID
     */
    @JsonProperty("post_id")
    private String postId;

    /**
     * 帖子URL
     */
    private String url;

    /**
     * 审核状态: PASSED / REJECTED / UNCERTAIN
     */
    @JsonProperty("audit_status")
    private String status;

    /**
     * 驳回原因列表
     */
    private List<RejectReason> reasons;

    /**
     * 置信度 (0-1)
     */
    @JsonProperty("confidence_score")
    private Double confidenceScore;

    /**
     * 建议处理方式
     */
    @JsonProperty("suggested_action")
    private String suggestedAction;

    /**
     * 风险等级: LOW / MEDIUM / HIGH / CRITICAL
     */
    @JsonProperty("risk_level")
    private String riskLevel;

    /**
     * 使用的LLM模型名称
     */
    @JsonProperty("model_name")
    private String modelName;

    /**
     * 审核时间
     */
    @JsonProperty("audited_time")
    private LocalDateTime auditedTime;

    /**
     * 驳回原因详情
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RejectReason {
        /**
         * 审核维度: title / content / tag / image
         */
        private String dimension;

        /**
         * 具体原因
         */
        private String reason;

        /**
         * 严重程度: LOW / MEDIUM / HIGH / CRITICAL
         */
        private String severity;
    }

    /**
     * 工厂方法: 创建UNCERTAIN决策
     */
    public static AuditDecision uncertain(String postId, String reason) {
        return AuditDecision.builder()
                .postId(postId)
                .status("UNCERTAIN")
                .reasons(List.of(new RejectReason("system", reason, "LOW")))
                .confidenceScore(0.0)
                .suggestedAction("人工复核")
                .riskLevel("UNKNOWN")
                .auditedTime(LocalDateTime.now())
                .build();
    }

    /**
     * 工厂方法: 创建PASSED决策
     */
    public static AuditDecision passed(String postId, double confidenceScore) {
        return AuditDecision.builder()
                .postId(postId)
                .status("PASSED")
                .reasons(List.of())
                .confidenceScore(confidenceScore)
                .suggestedAction("通过")
                .riskLevel("LOW")
                .auditedTime(LocalDateTime.now())
                .build();
    }

    /**
     * 工厂方法: 创建REJECTED决策
     */
    public static AuditDecision rejected(String postId, List<RejectReason> reasons, double confidenceScore) {
        return AuditDecision.builder()
                .postId(postId)
                .status("REJECTED")
                .reasons(reasons)
                .confidenceScore(confidenceScore)
                .suggestedAction("驳回")
                .riskLevel(calculateRiskLevel(reasons))
                .auditedTime(LocalDateTime.now())
                .build();
    }

    /**
     * 根据驳回原因计算风险等级
     */
    private static String calculateRiskLevel(List<RejectReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "LOW";
        }

        boolean hasCritical = reasons.stream()
                .anyMatch(r -> "CRITICAL".equals(r.getSeverity()));
        if (hasCritical) {
            return "CRITICAL";
        }

        boolean hasHigh = reasons.stream()
                .anyMatch(r -> "HIGH".equals(r.getSeverity()));
        if (hasHigh) {
            return "HIGH";
        }

        boolean hasMedium = reasons.stream()
                .anyMatch(r -> "MEDIUM".equals(r.getSeverity()));
        if (hasMedium) {
            return "MEDIUM";
        }

        return "LOW";
    }
}
