package com.xhs.audit.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务列表项DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobListItem {
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
}
