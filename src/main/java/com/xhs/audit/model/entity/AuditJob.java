package com.xhs.audit.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核任务实体
 * <p>
 * 记录审核任务的完整生命周期信息，包括：
 * <ul>
 *   <li>任务基本信息和状态</li>
 *   <li>处理进度统计</li>
 *   <li>时间戳记录</li>
 * </ul>
 * <p>
 * 任务状态流转：
 * <pre>
 * PENDING(等待处理) → PROCESSING(处理中) → [COMPLETED|PARTIAL_SUCCESS|FAILED]
 * PENDING → RETRYING → [PROCESSING|CRAWLED|AUDITING]
 * </pre>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Entity
@Table(name = "audit_job", indexes = {
        @Index(name = "idx_job_id", columnList = "job_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditJob {

    /**
     * 数据库主键，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务主键，UUID格式
     * <p>
     * 用于API调用中标识任务，如：/api/audit/result/{jobId}
     */
    @Column(nullable = false, unique = true, length = 100)
    private String jobId;

    /**
     * 原始URL（单个任务时使用）
     * <p>
     * v4.0新增：批量任务时此字段可能为空，具体URL存储在xhs_content表
     */
    @Column(length = 500)
    private String url;

    /**
     * 任务总链接数
     * <p>
     * 单任务为1，批量任务为Excel中的URL数量
     */
    @Column(nullable = false)
    private Integer totalLinks;

    /**
     * 已完成处理的链接数
     * <p>
     * 包括成功和失败的总数
     */
    @Column(nullable = false)
    private Integer completedCount = 0;

    /**
     * 成功处理的链接数
     * <p>
     * 审核结果为PASSED的链接数量
     */
    @Column(nullable = false)
    private Integer successCount = 0;

    /**
     * 处理失败的链接数
     * <p>
     * 审核结果为REJECTED或UNCERTAIN的链接数量
     */
    @Column(nullable = false)
    private Integer failedCount = 0;

    /**
     * 任务状态
     * <p>
     * 状态枚举：{@link TaskStatus}
     * <ul>
     *   <li>PENDING - 等待处理</li>
     *   <li>PROCESSING - 处理中</li>
     *   <li>CRAWLING - 爬取中</li>
     *   <li>CRAWLED - 爬取完成</li>
     *   <li>AUDITING - 审核中</li>
     *   <li>RETRYING - 重试中</li>
     *   <li>COMPLETED - 全部成功</li>
     *   <li>PARTIAL_SUCCESS - 部分成功</li>
     *   <li>FAILED - 全部失败</li>
     * </ul>
     */
    @Column(length = 20)
    private String status;

    /**
     * 状态消息/错误信息
     * <p>
     * 记录当前状态的详细信息，如失败原因、重试次数等
     */
    @Column(length = 500)
    private String message;

    /**
     * 源文件名（批量任务时使用）
     * <p>
     * 记录上传的Excel文件名，便于追溯数据来源
     */
    @Column(length = 255)
    private String fileName;

    /**
     * 任务创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 最后更新时间
     * <p>
     * 由JPA @PreUpdate自动维护
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 任务完成时间
     * <p>
     * 仅在任务进入终态(COMPLETED|PARTIAL_SUCCESS|FAILED)时设置
     */
    private LocalDateTime completedAt;

    /**
     * JPA实体更新前回调
     * <p>
     * 自动更新updatedAt字段
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 原子性增加已完成计数
     * <p>
     * 用于多线程环境下的安全计数
     */
    public synchronized void incrementCompletedCount() {
        this.completedCount++;
    }

    /**
     * 增加成功计数
     * <p>
     * 当单个链接审核通过时调用
     */
    public synchronized void incrementSuccessCount() {
        this.successCount++;
    }

    /**
     * 增加失败计数
     * <p>
     * 当单个链接审核不通过时调用
     */
    public synchronized void incrementFailedCount() {
        this.failedCount++;
    }

    /**
     * 检查任务是否完成
     * <p>
     * 判断条件：已完成数量 >= 总数量
     *
     * @return true-已完成，false-未完成
     */
    public boolean isCompleted() {
        return completedCount >= totalLinks;
    }

    /**
     * 计算进度百分比
     * <p>
     * 用于展示任务进度，范围0-100
     *
     * @return 进度百分比
     */
    public int getProgressPercent() {
        if (totalLinks == 0)
            return 0;
        return (completedCount * 100) / totalLinks;
    }
}
