package com.xhs.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.xhs.audit.model.entity.AuditRule;

/**
 * 审核规则Repository
 */
@Repository
public interface AuditRuleRepository extends JpaRepository<AuditRule, Long> {

    List<AuditRule> findByEnabledTrueOrderByPriorityAsc();

    List<AuditRule> findByDimensionAndEnabledTrue(String dimension);
}
