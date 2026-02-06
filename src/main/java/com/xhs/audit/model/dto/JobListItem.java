package com.xhs.audit.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务列表项DTO
 * <p>
 * 用于任务列表页面展示，包含任务的基本统计信息。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>统计字段：totalLinks、completedCount、passedCount、rejectedCount 用于进度展示</li>
 *   <li>状态字段：status、progressPercent 反映任务当前执行情况</li>
 *   <li>时间字段：createdAt、completedAt 用于时间排序</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobListItem {

    /**
     * 任务ID
     * <p>
     * 格式：UUID字符串，用于唯一标识一个审核任务
     */
    private String jobId;

    /**
     * 文件名
     * <p>
     * 上传审核的文件名称，用于任务识别
     */
    private String fileName;

    /**
     * 总链接数
     * <p>
     * 任务中待审核的链接总数
     */
    private Integer totalLinks;

    /**
     * 已完成数
     * <p>
     * 已完成审核的链接数量
     */
    private Integer completedCount;

    /**
     * 通过数
     * <p>
     * 审核结果为 PASSED 的链接数量
     */
    private Integer passedCount;

    /**
     * 驳回数
     * <p>
     * 审核结果为 REJECTED 的链接数量
     */
    private Integer rejectedCount;

    /**
     * 任务状态
     * <p>
     * 取值范围：PENDING（等待处理）、PROCESSING（处理中）、COMPLETED（已完成）、
     * FAILED（失败）、PARTIAL_SUCCESS（部分成功）
     */
    private String status;

    /**
     * 进度百分比
     * <p>
     * 0-100的整数，计算公式：completedCount / totalLinks * 100
     */
    private Integer progressPercent;

    /**
     * 创建时间
     * <p>
     * 任务创建的时间戳
     */
    private LocalDateTime createdAt;

    /**
     * 完成时间
     * <p>
     * 任务完成的时间戳，任务未完成时为null
     */
    private LocalDateTime completedAt;
}
