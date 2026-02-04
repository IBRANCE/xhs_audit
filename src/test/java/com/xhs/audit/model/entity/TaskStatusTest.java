package com.xhs.audit.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskStatus 枚举测试
 * v4.0: 验证任务状态流转逻辑
 *
 * @author XHS Audit System
 * @since 2026-02-03
 */
@DisplayName("TaskStatus - 任务状态枚举测试")
class TaskStatusTest {

    @Test
    @DisplayName("终态判断 - COMPLETED 和 FAILED 是终态")
    void testIsTerminal() {
        assertTrue(TaskStatus.COMPLETED.isTerminal());
        assertTrue(TaskStatus.FAILED.isTerminal());
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.CRAWLING.isTerminal());
        assertFalse(TaskStatus.CRAWLED.isTerminal());
        assertFalse(TaskStatus.AUDITING.isTerminal());
        assertFalse(TaskStatus.RETRYING.isTerminal());
    }

    @Test
    @DisplayName("成功状态判断 - 只有 COMPLETED 是成功")
    void testIsSuccess() {
        assertTrue(TaskStatus.COMPLETED.isSuccess());
        assertFalse(TaskStatus.FAILED.isSuccess());
        assertFalse(TaskStatus.PENDING.isSuccess());
        assertFalse(TaskStatus.CRAWLING.isSuccess());
        assertFalse(TaskStatus.RETRYING.isSuccess());
    }

    @Test
    @DisplayName("所有状态值存在")
    void testAllStatusValuesExist() {
        TaskStatus[] statuses = TaskStatus.values();
        assertEquals(7, statuses.length);

        assertNotNull(TaskStatus.PENDING);
        assertNotNull(TaskStatus.CRAWLING);
        assertNotNull(TaskStatus.CRAWLED);
        assertNotNull(TaskStatus.AUDITING);
        assertNotNull(TaskStatus.COMPLETED);
        assertNotNull(TaskStatus.FAILED);
        assertNotNull(TaskStatus.RETRYING);
    }

    @Test
    @DisplayName("任务生命周期测试 - 从 PENDING 到 COMPLETED")
    void testLifecycle() {
        TaskStatus current = TaskStatus.PENDING;

        // 模拟正常流程
        assertFalse(current.isTerminal());
        assertFalse(current.isSuccess());

        current = TaskStatus.CRAWLING;
        assertFalse(current.isTerminal());

        current = TaskStatus.CRAWLED;
        assertFalse(current.isTerminal());

        current = TaskStatus.AUDITING;
        assertFalse(current.isTerminal());

        current = TaskStatus.COMPLETED;
        assertTrue(current.isTerminal());
        assertTrue(current.isSuccess());
    }

    @Test
    @DisplayName("任务失败流程测试 - 从 PENDING 到 FAILED")
    void testFailureLifecycle() {
        TaskStatus current = TaskStatus.PENDING;

        current = TaskStatus.CRAWLING;
        current = TaskStatus.FAILED;

        assertTrue(current.isTerminal());
        assertFalse(current.isSuccess());
    }

    @Test
    @DisplayName("重试流程测试")
    void testRetryLifecycle() {
        TaskStatus current = TaskStatus.PENDING;

        current = TaskStatus.CRAWLING;
        current = TaskStatus.RETRYING;
        current = TaskStatus.CRAWLING;
        current = TaskStatus.COMPLETED;

        assertTrue(current.isTerminal());
        assertTrue(current.isSuccess());
    }
}
