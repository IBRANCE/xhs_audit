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
 * 审核任务Repository
 */
@Repository
public interface AuditJobRepository extends JpaRepository<AuditJob, Long>, JpaSpecificationExecutor<AuditJob> {

    Optional<AuditJob> findByJobId(String jobId);

    /**
     * 分页查询任务列表，按创建时间倒序
     */
    Page<AuditJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 根据状态分页查询任务列表
     */
    Page<AuditJob> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * 模糊搜索文件名查询任务列表
     */
    @Query("SELECT j FROM AuditJob j WHERE j.fileName LIKE %:keyword% ORDER BY j.createdAt DESC")
    Page<AuditJob> searchByFileName(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 根据任务ID精确查询（用于筛选）
     */
    @Query("SELECT j FROM AuditJob j WHERE j.jobId = :jobId ORDER BY j.createdAt DESC")
    Page<AuditJob> findByJobId(@Param("jobId") String jobId, Pageable pageable);
}
