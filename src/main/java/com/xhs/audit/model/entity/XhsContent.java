package com.xhs.audit.model.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小红书内容实体
 */
@Entity
@Table(name = "xhs_content", indexes = {
        @Index(name = "idx_post_id", columnList = "post_id"),
        @Index(name = "idx_author_id", columnList = "author_id"),
        @Index(name = "idx_crawled_at", columnList = "crawled_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XhsContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 256)
    private String postId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> images;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(length = 256)
    private String authorId;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime crawledAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
