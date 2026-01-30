package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务详情DTO，包含任务信息和所有链接的审核结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailResponse {
    // 任务基本信息
    private String jobId;
    private String fileName;
    private Integer totalLinks;
    private Integer completedCount;
    private Integer passedCount;
    private Integer rejectedCount;
    private String status;
    private Integer progressPercent;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // 审核结果列表（分页）
    private List<AuditResultItem> results;
    private Integer totalResults;
    private Integer currentPage;
    private Integer pageSize;
    private Integer totalPages;
}
