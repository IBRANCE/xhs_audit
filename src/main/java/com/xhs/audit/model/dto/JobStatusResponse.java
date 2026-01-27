package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务状态响应
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusResponse {

    /**
     * 任务ID
     */
    private String jobId;

    /**
     * 总链接数
     */
    private Integer totalLinks;

    /**
     * 已完成数
     */
    private Integer completedCount;

    /**
     * 通过数
     */
    private Integer passedCount;

    /**
     * 驳回数
     */
    private Integer rejectedCount;

    /**
     * 任务状态: PENDING/PROCESSING/COMPLETED/FAILED/PARTIAL_SUCCESS
     */
    private String status;

    /**
     * 进度百分比 (0-100)
     */
    private Integer progressPercent;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 预计完成时间
     */
    private LocalDateTime estimatedCompletionTime;

    /**
     * 错误数
     */
    private Integer errorCount;

    /**
     * 错误摘要
     */
    private List<String> errorSummary;
}
