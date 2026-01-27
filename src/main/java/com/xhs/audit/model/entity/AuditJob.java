package com.xhs.audit.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核任务实体
 */
@Entity
@Table(name = "audit_job", indexes = {
        @Index(name = "idx_job_id", columnList = "job_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String jobId;

    @Column(nullable = false)
    private Integer totalLinks;

    @Column(nullable = false)
    private Integer completedCount = 0;

    @Column(nullable = false)
    private Integer successCount = 0;

    @Column(nullable = false)
    private Integer failedCount = 0;

    @Column(length = 20)
    private String status; // PENDING / PROCESSING / COMPLETED / PARTIAL_SUCCESS / FAILED

    @Column(length = 255)
    private String fileName;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 原子性增加已完成计数
     */
    public synchronized void incrementCompletedCount() {
        this.completedCount++;
    }

    /**
     * 增加成功计数
     */
    public synchronized void incrementSuccessCount() {
        this.successCount++;
    }

    /**
     * 增加失败计数
     */
    public synchronized void incrementFailedCount() {
        this.failedCount++;
    }

    /**
     * 检查任务是否完成
     */
    public boolean isCompleted() {
        return completedCount >= totalLinks;
    }

    /**
     * 计算进度百分比
     */
    public int getProgressPercent() {
        if (totalLinks == 0)
            return 0;
        return (completedCount * 100) / totalLinks;
    }
}
