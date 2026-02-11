package com.xhs.audit.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistent configuration for content validation rules.
 */
@Entity
@Table(name = "audit_rule_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditRuleConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "min_text_length", nullable = false)
    private Integer minTextLength;

    @Column(name = "min_image_count", nullable = false)
    private Integer minImageCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_tags")
    @Builder.Default
    private List<String> requiredTags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "car_model_names")
    @Builder.Default
    private List<String> carModelNames = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_tags")
    @Builder.Default
    private List<String> excludedTags = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
