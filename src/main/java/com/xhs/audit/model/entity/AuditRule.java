package com.xhs.audit.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核规则实体
 */
@Entity
@Table(name = "audit_rule", indexes = {
        @Index(name = "idx_dimension", columnList = "dimension"),
        @Index(name = "idx_enabled", columnList = "enabled"),
        @Index(name = "idx_priority", columnList = "priority")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String dimension; // title / content / tag / image

    @Column(nullable = false, length = 50)
    private String ruleType; // sensitive_word / length / pattern / custom

    @Column(length = 200)
    private String ruleName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String ruleContent;

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
