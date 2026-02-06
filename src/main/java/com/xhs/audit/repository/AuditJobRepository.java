package com.xhs.audit.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.xhs.audit.model.entity.AuditJob;

/**
 * 审核任务数据访问接口
 * <p>
 * 提供 {@link AuditJob} 实体类的CRUD和查询操作。
 * <p>
 * 查询分类：
 * <ul>
 *   <li>主键查询：findByJobId</li>
 *   <li>状态查询：findByStatusOrderByCreatedAtDesc</li>
 *   <li>分页列表：findAllByOrderByCreatedAtDesc</li>
 *   <li>模糊搜索：searchByFileName</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Repository
public interface AuditJobRepository extends JpaRepository<AuditJob, Long>, JpaSpecificationExecutor<AuditJob> {

    /**
     * 根据任务ID查询
     * <p>
     * 使用唯一业务主键查询，支持缓存命中
     *
     * @param jobId 任务ID（UUID格式）
     * @return 任务实体，不存在则返回Optional.empty()
     */
    Optional<AuditJob> findByJobId(String jobId);

    /**
     * 分页查询所有任务
     * <p>
     * 按创建时间倒序排列，最新任务在前
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<AuditJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 根据状态分页查询任务
     * <p>
     * 筛选特定状态的任务，如查询所有"处理中"的任务
     *
     * @param status   任务状态
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<AuditJob> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * 根据文件名模糊搜索任务
     * <p>
     * 支持文件名部分匹配，用于筛选特定上传文件的任务
     *
     * @param keyword  搜索关键词
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("SELECT j FROM AuditJob j WHERE j.fileName LIKE %:keyword% ORDER BY j.createdAt DESC")
    Page<AuditJob> searchByFileName(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 根据任务ID精确查询（用于筛选）
     * <p>
     * 精确匹配任务ID，返回分页结果（通常只有一条）
     *
     * @param jobId    任务ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("SELECT j FROM AuditJob j WHERE j.jobId = :jobId ORDER BY j.createdAt DESC")
    Page<AuditJob> findByJobId(@Param("jobId") String jobId, Pageable pageable);
}
