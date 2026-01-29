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
 * 审核结果Repository
 */
@Repository
public interface AuditResultRepository extends JpaRepository<AuditResult, Long> {

    Optional<AuditResult> findByPostId(String postId);

    Optional<AuditResult> findFirstByPostIdOrderByAuditedAtDesc(String postId);

    List<AuditResult> findByJobId(String jobId);

    Page<AuditResult> findByJobId(String jobId, Pageable pageable);

    Page<AuditResult> findByAuditStatus(String status, Pageable pageable);

    long countByJobIdAndAuditStatus(String jobId, String status);

    @Query("SELECT r.auditStatus, COUNT(r) FROM AuditResult r WHERE r.jobId = :jobId GROUP BY r.auditStatus")
    List<Object[]> getStatusStatsByJobId(@Param("jobId") String jobId);

    List<AuditResult> findByAuditedAtBetween(LocalDateTime start, LocalDateTime end);
}
