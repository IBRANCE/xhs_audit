package com.xhs.audit.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 敏感词实体
 */
@Entity
@Table(name = "sensitive_word", indexes = {
        @Index(name = "idx_word", columnList = "word"),
        @Index(name = "idx_category", columnList = "category"),
        @Index(name = "idx_severity", columnList = "severity")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensitiveWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String word;

    @Column(length = 50)
    private String category; // politics / violence / adult / spam / other

    @Column(length = 20)
    private String severity; // high / medium / low

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
