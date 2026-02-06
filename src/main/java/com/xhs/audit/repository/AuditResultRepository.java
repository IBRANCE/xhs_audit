package com.xhs.audit.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.xhs.audit.model.entity.AuditResult;

/**
 * 审核结果数据访问接口
 * <p>
 * 提供 {@link AuditResult} 实体类的CRUD和查询操作。
 * <p>
 * 查询分类：
 * <ul>
 *   <li>单条查询：findByPostId, findFirstByPostIdOrderByAuditedAtDesc</li>
 *   <li>任务查询：findByJobId, countByJobIdAndAuditStatus</li>
 *   <li>状态查询：findByAuditStatus</li>
 *   <li>统计查询：getStatusStatsByJobId</li>
 *   <li>时间范围查询：findByAuditedAtBetween</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Repository
public interface AuditResultRepository extends JpaRepository<AuditResult, Long> {

    /**
     * 根据帖子ID查询所有审核结果
     * <p>
     * 同一帖子可能有多次审核记录（复审场景）
     *
     * @param postId 帖子ID
     * @return 审核结果列表
     */
    Optional<AuditResult> findByPostId(String postId);

    /**
     * 根据帖子ID查询最新审核结果
     * <p>
     * 按审核时间倒序，取第一条
     *
     * @param postId 帖子ID
     * @return 最新审核结果
     */
    Optional<AuditResult> findFirstByPostIdOrderByAuditedAtDesc(String postId);

    /**
     * 根据任务ID查询所有审核结果
     *
     * @param jobId 任务ID
     * @return 审核结果列表
     */
    List<AuditResult> findByJobId(String jobId);

    /**
     * 根据任务ID分页查询审核结果
     *
     * @param jobId    任务ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<AuditResult> findByJobId(String jobId, Pageable pageable);

    /**
     * 根据审核状态分页查询
     * <p>
     * 筛选特定状态的审核结果，如查询所有"通过"的结果
     *
     * @param status   审核状态
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<AuditResult> findByAuditStatus(String status, Pageable pageable);

    /**
     * 统计任务中特定状态的审核结果数量
     *
     * @param jobId  任务ID
     * @param status 审核状态
     * @return 数量
     */
    long countByJobIdAndAuditStatus(String jobId, String status);

    /**
     * 获取任务的状态统计
     * <p>
     * 返回各状态的计数，如：[{status: "PASSED", count: 95}, {status: "REJECTED", count: 5}]
     *
     * @param jobId 任务ID
     * @return 状态统计列表
     */
    @Query("SELECT r.auditStatus, COUNT(r) FROM AuditResult r WHERE r.jobId = :jobId GROUP BY r.auditStatus")
    List<Object[]> getStatusStatsByJobId(@Param("jobId") String jobId);

    /**
     * 根据时间范围查询审核结果
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 审核结果列表
     */
    List<AuditResult> findByAuditedAtBetween(LocalDateTime start, LocalDateTime end);
}
