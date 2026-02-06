package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核详情响应DTO
 * <p>
 * 用于前端详情弹窗展示，包含小红书内容（xhs_content）和审核结果（audit_result）的完整字段。
 * <p>
 * 数据来源：
 * <ul>
 *   <li>xhs_content表：postId、url、title、content、images、tags、authorId、publishedAt、crawledAt</li>
 *   <li>audit_result表：jobId、status、reasons、riskLevel、confidenceScore、modelName、auditedTime</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditDetailResponse {

    // ==================== xhs_content 表字段 ====================

    /**
     * 帖子ID
     * <p>
     * 小红书笔记的唯一标识符
     */
    @JsonProperty("post_id")
    private String postId;

    /**
     * 帖子URL
     * <p>
     * 小红书笔记的完整访问地址
     */
    private String url;

    /**
     * 笔记标题
     * <p>
     * 笔记的标题文本
     */
    private String title;

    /**
     * 笔记正文内容
     * <p>
     * 笔记的正文文本内容
     */
    private String content;

    /**
     * 笔记图片列表
     * <p>
     * 笔记中包含的图片URL列表
     */
    private List<String> images;

    /**
     * 笔记标签列表
     * <p>
     * 笔记关联的标签列表
     */
    private List<String> tags;

    /**
     * 作者ID
     * <p>
     * 笔记作者的userId
     */
    @JsonProperty("author_id")
    private String authorId;

    /**
     * 发布时间
     * <p>
     * 笔记原始发布时间
     */
    @JsonProperty("published_at")
    private LocalDateTime publishedAt;

    /**
     * 爬取时间
     * <p>
     * 系统爬取该笔记的时间
     */
    @JsonProperty("crawled_at")
    private LocalDateTime crawledAt;

    // ==================== audit_result 表字段 ====================

    /**
     * 任务ID
     * <p>
     * 关联的审核任务ID
     */
    @JsonProperty("job_id")
    private String jobId;

    /**
     * 审核状态
     * <p>
     * 取值：PASSED（通过）、REJECTED（驳回）、UNCERTAIN（不确定）
     */
    private String status;

    /**
     * 驳回原因列表
     * <p>
     * 审核不通过时的具体原因，每个原因包含维度、描述和严重程度
     */
    private List<AuditResultItem.RejectReason> reasons;

    /**
     * 风险等级
     * <p>
     * 内容风险评估等级：LOW（低风险）、MEDIUM（中风险）、HIGH（高风险）、CRITICAL（极高风险）
     */
    @JsonProperty("risk_level")
    private String riskLevel;

    /**
     * 置信度分数
     * <p>
     * 0-1之间的双精度浮点数，表示审核判断的置信程度
     */
    @JsonProperty("confidence_score")
    private Double confidenceScore;

    /**
     * 使用的LLM模型名称
     * <p>
     * 执行审核的AI模型名称，如 qwen/qwen3-4b
     */
    @JsonProperty("model_name")
    private String modelName;

    /**
     * 审核时间
     * <p>
     * 执行审核操作的时间戳
     */
    @JsonProperty("audited_time")
    private LocalDateTime auditedTime;
}
