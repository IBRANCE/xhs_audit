package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务详情DTO
 * <p>
 * 用于任务详情页面展示，包含任务的基本信息和审核结果分页列表。
 * <p>
 * 结构说明：
 * <ul>
 *   <li>任务基本信息：jobId、fileName、统计字段、时间字段</li>
 *   <li>分页信息：results、totalResults、currentPage、pageSize、totalPages</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailResponse {

    // ==================== 任务基本信息 ====================

    /**
     * 任务ID
     * <p>
     * 唯一标识该审核任务的UUID字符串
     */
    private String jobId;

    /**
     * 文件名
     * <p>
     * 原始上传文件名，用于任务识别和展示
     */
    private String fileName;

    /**
     * 总链接数
     * <p>
     * 该任务中待审核的链接总数
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
     * 取值：PENDING、PROCESSING、COMPLETED、FAILED、PARTIAL_SUCCESS
     */
    private String status;

    /**
     * 进度百分比
     * <p>
     * 0-100的整数，表示任务完成进度
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

    // ==================== 审核结果分页信息 ====================

    /**
     * 当前页审核结果列表
     * <p>
     * 分页查询的审核结果数据，每条包含 postId、url、status、reasons 等字段
     */
    private List<AuditResultItem> results;

    /**
     * 总结果数
     * <p>
     * 该任务下所有审核结果的总数，用于前端分页计算
     */
    private Integer totalResults;

    /**
     * 当前页码
     * <p>
     * 从0开始的当前页码
     */
    private Integer currentPage;

    /**
     * 每页大小
     * <p>
     * 每页显示的结果数量，默认10条
     */
    private Integer pageSize;

    /**
     * 总页数
     * <p>
     * 根据totalResults和pageSize计算的总页数
     */
    private Integer totalPages;
}
