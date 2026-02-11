package com.xhs.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xhs.audit.model.entity.AuditRuleConfig;

public interface AuditRuleConfigRepository extends JpaRepository<AuditRuleConfig, Long> {
}
