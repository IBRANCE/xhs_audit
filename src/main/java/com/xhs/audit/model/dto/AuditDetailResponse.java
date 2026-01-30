package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核详情响应
 * 用于前端详情弹窗展示，包含 xhs_content 和 audit_result 的字段
 *
 * @author XHS Audit System
 * @since 2026-01-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditDetailResponse {

    // ===== xhs_content 表字段 =====
    @JsonProperty("post_id")
    private String postId;

    private String url;

    private String title;

    private String content;

    private List<String> images;

    private List<String> tags;

    @JsonProperty("author_id")
    private String authorId;

    @JsonProperty("published_at")
    private LocalDateTime publishedAt;

    @JsonProperty("crawled_at")
    private LocalDateTime crawledAt;

    // ===== audit_result 表字段 =====
    @JsonProperty("job_id")
    private String jobId;

    private String status;

    private List<AuditResultItem.RejectReason> reasons;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("audited_time")
    private LocalDateTime auditedTime;
}
