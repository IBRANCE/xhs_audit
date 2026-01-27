package com.xhs.audit.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核结果实体
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String postId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String jobId;

    @Column(length = 20)
    private String auditStatus; // PASSED / REJECTED / UNCERTAIN

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> reasons;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(length = 100)
    private String modelName;

    @Column(nullable = false)
    private LocalDateTime auditedAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
