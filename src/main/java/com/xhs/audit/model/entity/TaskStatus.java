package com.xhs.audit.model.entity;

/**
 * 任务状态枚举
 * 用于跟踪异步任务的完整生命周期
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
public enum TaskStatus {
    /**
     * 等待处理 - 任务已创建，等待爬取
     */
    PENDING,

    /**
     * 爬取中 - 正在执行网页爬取
     */
    CRAWLING,

    /**
     * 爬取完成 - 内容已爬取，等待审核
     */
    CRAWLED,

    /**
     * 审核中 - 正在执行 LLM 审核
     */
    AUDITING,

    /**
     * 完成 - 审核已完成
     */
    COMPLETED,

    /**
     * 失败 - 任务执行失败（爬取或审核失败）
     */
    FAILED,

    /**
     * 重试中 - 失败后正在重试
     */
    RETRYING;

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * 判断是否为成功状态
     */
    public boolean isSuccess() {
        return this == COMPLETED;
    }

    /**
     * 检查状态转换是否合法
     * v4.0: 状态机保护，防止非法状态转换
     */
    public static boolean isAllowedTransition(String currentStatus, String newStatus) {
        try {
            TaskStatus current = TaskStatus.valueOf(currentStatus);
            TaskStatus target = TaskStatus.valueOf(newStatus);
            return isAllowedTransition(current, target);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 检查状态转换是否合法（枚举版本）
     */
    public static boolean isAllowedTransition(TaskStatus current, TaskStatus target) {
        // 同状态允许（幂等性）
        if (current == target) {
            return true;
        }

        // 定义允许的状态转换
        return switch (current) {
            case PENDING -> target == CRAWLING || target == FAILED;
            case CRAWLING -> target == CRAWLED || target == FAILED || target == RETRYING;
            case CRAWLED -> target == AUDITING || target == FAILED;
            case AUDITING -> target == COMPLETED || target == FAILED || target == RETRYING;
            case RETRYING -> target == CRAWLING || target == AUDITING || target == FAILED;
            case COMPLETED, FAILED -> false; // 终态不允许转换
        };
    }
}
