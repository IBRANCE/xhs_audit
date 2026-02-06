package com.xhs.audit.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核结果实体
 * <p>
 * 记录AI对单条内容的审核结果，是审核系统的核心数据。
 * <p>
 * 数据来源：由 {@link com.xhs.audit.agent.ContentAuditAgent} 生成
 * <p>
 * 审核状态说明：
 * <ul>
 *   <li>PASSED - 内容合规，审核通过</li>
 *   <li>REJECTED - 内容违规，已被拒绝</li>
 *   <li>UNCERTAIN - 无法判定，需要人工复核</li>
 * </ul>
 * <p>
 * 驳回原因维度：
 * <ul>
 *   <li>title - 标题违规</li>
 *   <li>content - 正文违规</li>
 *   <li>tag - 标签违规</li>
 *   <li>image - 图片违规</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Entity
@Table(name = "audit_result", indexes = {
        @Index(name = "idx_post_id", columnList = "post_id"),
        @Index(name = "idx_job_id", columnList = "job_id"),
        @Index(name = "idx_audit_status", columnList = "audit_status"),
        @Index(name = "idx_audited_at", columnList = "audited_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditResult {

    /**
     * 数据库主键，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 小红书笔记ID
     * <p>
     * 关联 {@link XhsContent#postId}，一对多关系
     * 同一篇笔记可能有多次审核记录（复审场景）
     */
    @Column(nullable = false, length = 256)
    private String postId;

    /**
     * 笔记URL
     * <p>
     * 冗余字段，便于查询时快速获取URL
     */
    @Column(nullable = false, length = 500)
    private String url;

    /**
     * 关联的任务ID
     * <p>
     * 关联 {@link AuditJob#jobId}，标识这次审核属于哪个任务
     */
    @Column(length = 100)
    private String jobId;

    /**
     * 审核状态
     * <p>
     * 枚举值：
     * <ul>
     *   <li>PASSED - 通过，内容合规</li>
     *   <li>REJECTED - 拒绝，内容违规</li>
     *   <li>UNCERTAIN - 不确定，需要人工复核</li>
     * </ul>
     */
    @Column(length = 20)
    private String auditStatus;

    /**
     * 驳回原因列表
     * <p>
     * JSON数组格式，每个元素包含：
     * <ul>
     *   <li>dimension - 违规维度（title/content/tag/image）</li>
     *   <li>reason - 具体原因描述</li>
     *   <li>severity - 严重程度（LOW/MEDIUM/HIGH/CRITICAL）</li>
     * </ul>
     * 仅在 auditStatus != PASSED 时有值
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> reasons;

    /**
     * 置信度分数
     * <p>
     * 范围：0.0 - 1.0
     * <ul>
     *   <li>>= 0.9 - 高置信度</li>
     *   <li>0.7 - 0.9 - 中高置信度</li>
     *   <li>0.5 - 0.7 - 中置信度</li>
     *   <li>< 0.5 - 低置信度</li>
     * </ul>
     */
    @Column(precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    /**
     * 使用的AI模型名称
     * <p>
     * 格式：{provider}/{model}
     * 示例：openai/gpt-4, zhipuai/glm-4
     */
    @Column(length = 100)
    private String modelName;

    /**
     * 审核时间
     * <p>
     * 记录AI完成审核的时间戳
     */
    @Column(nullable = false)
    private LocalDateTime auditedAt = LocalDateTime.now();

    /**
     * 数据库记录创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
