package com.xhs.audit.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务统计数据DTO
 * 用于批量查询场景，避免N+1问题
 * 
 * @author XHS Audit System
 * @since 2026-02-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatistics {

    /**
     * 任务ID
     */
    private String jobId;

    /**
     * 已完成数量
     */
    private Integer completedCount;

    /**
     * 通过数量
     */
    private Integer passedCount;

    /**
     * 驳回数量（REJECTED）
     */
    private Integer rejectedCount;

    /**
     * 不确定数量（UNCERTAIN）
     */
    private Integer uncertainCount;

    /**
     * 实际驳回数量（REJECTED + UNCERTAIN）
     */
    public Integer getActualRejectedCount() {
        return (rejectedCount != null ? rejectedCount : 0) +
                (uncertainCount != null ? uncertainCount : 0);
    }

    /**
     * 计算进度百分比
     * 
     * @param totalLinks 总链接数
     * @return 进度百分比（0-100）
     */
    public Integer calculateProgressPercent(Integer totalLinks) {
        if (totalLinks == null || totalLinks == 0 || completedCount == null) {
            return 0;
        }
        return (int) (completedCount * 100L / totalLinks);
    }
}
