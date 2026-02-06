package com.xhs.audit.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核结果搜索条件
 * 用于封装 searchAuditResults 方法的多个查询参数
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSearchCriteria {

    /**
     * 帖子ID
     */
    private String postId;

    /**
     * 任务ID
     */
    private String jobId;

    /**
     * 审核状态: PASSED / REJECTED / UNCERTAIN
     */
    private String status;

    /**
     * 搜索开始时间
     */
    private LocalDateTime startDate;

    /**
     * 搜索结束时间
     */
    private LocalDateTime endDate;
}
