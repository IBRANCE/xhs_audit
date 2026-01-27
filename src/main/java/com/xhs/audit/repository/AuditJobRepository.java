package com.xhs.audit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.xhs.audit.model.entity.AuditJob;

/**
 * 审核任务Repository
 */
@Repository
public interface AuditJobRepository extends JpaRepository<AuditJob, Long> {

    Optional<AuditJob> findByJobId(String jobId);
}
