package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史审核结果列表项
 * 查询 audit_result 表，只展示 audit_result 的字段
 *
 * @author XHS Audit System
 * @since 2026-01-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResultItem {

    /**
     * 帖子ID
     */
    @JsonProperty("post_id")
    private String postId;

    /**
     * 任务ID
     */
    @JsonProperty("job_id")
    private String jobId;

    /**
     * 帖子URL
     */
    private String url;

    /**
     * 审核状态: PASSED / REJECTED / UNCERTAIN
     */
    private String status;

    /**
     * 驳回原因列表
     */
    private List<RejectReason> reasons;

    /**
     * 风险等级: LOW / MEDIUM / HIGH / CRITICAL
     */
    @JsonProperty("risk_level")
    private String riskLevel;

    /**
     * 置信度 (0-1)
     */
    @JsonProperty("confidence_score")
    private Double confidenceScore;

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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
}
