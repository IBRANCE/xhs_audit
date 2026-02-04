package com.xhs.audit.model.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TaskStatus 状态转换测试
 * v4.0: 验证状态机转换规则
 *
 * @author XHS Audit System
 * @since 2026-02-03
 */
@DisplayName("TaskStatus - 状态转换规则测试")
class TaskStatusTransitionTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,CRAWLING,true",
            "PENDING,FAILED,true",
            "PENDING,AUDITING,false", // 不能跳过爬取
            "PENDING,COMPLETED,false", // 不能跳过所有步骤

            "CRAWLING,CRAWLED,true",
            "CRAWLING,FAILED,true",
            "CRAWLING,RETRYING,true",
            "CRAWLING,PENDING,false", // 不能回退

            "CRAWLED,AUDITING,true",
            "CRAWLED,FAILED,true",
            "CRAWLED,PENDING,false", // 不能回退

            "AUDITING,COMPLETED,true",
            "AUDITING,FAILED,true",
            "AUDITING,RETRYING,true",
            "AUDITING,CRAWLING,false", // 不能回退

            "RETRYING,CRAWLING,true",
            "RETRYING,AUDITING,true",
            "RETRYING,FAILED,true",
            "RETRYING,COMPLETED,false",

            "COMPLETED,PENDING,false", // 终态不能转换
            "COMPLETED,FAILED,false",
            "COMPLETED,RETRYING,false",

            "FAILED,PENDING,false", // 终态不能转换
            "FAILED,COMPLETED,false",
            "FAILED,RETRYING,false"
    })
    @DisplayName("参数化测试 - 状态转换规则")
    void testStateTransitionRules(String from, String to, boolean expected) {
        boolean result = TaskStatus.isAllowedTransition(from, to);
        assertEquals(expected, result,
                String.format("状态转换 %s -> %s 应该%s", from, to, expected ? "允许" : "禁止"));
    }

    @Test
    @DisplayName("同状态转换 - 幂等性")
    void testSameStateTransition() {
        for (TaskStatus status : TaskStatus.values()) {
            assertTrue(TaskStatus.isAllowedTransition(status, status),
                    "同状态转换应该始终允许: " + status);
        }
    }

    @Test
    @DisplayName("正常流程 - PENDING → CRAWLING → CRAWLED → AUDITING → COMPLETED")
    void testNormalWorkflow() {
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.PENDING, TaskStatus.CRAWLING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.CRAWLED));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLED, TaskStatus.AUDITING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.AUDITING, TaskStatus.COMPLETED));
    }

    @Test
    @DisplayName("爬取失败流程 - PENDING → CRAWLING → FAILED")
    void testCrawlFailureWorkflow() {
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.PENDING, TaskStatus.CRAWLING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.FAILED));
    }

    @Test
    @DisplayName("审核失败流程 - CRAWLED → AUDITING → FAILED")
    void testAuditFailureWorkflow() {
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLED, TaskStatus.AUDITING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.AUDITING, TaskStatus.FAILED));
    }

    @Test
    @DisplayName("重试流程 - CRAWLING → RETRYING → CRAWLING → CRAWLED")
    void testRetryWorkflow() {
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.RETRYING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.RETRYING, TaskStatus.CRAWLING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.CRAWLED));
    }

    @Test
    @DisplayName("终态不可转换")
    void testTerminalStatesCannotTransition() {
        // COMPLETED 不能转换到任何其他状态
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.COMPLETED, TaskStatus.PENDING));
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.COMPLETED, TaskStatus.CRAWLING));
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.COMPLETED, TaskStatus.FAILED));

        // FAILED 不能转换到任何其他状态
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.FAILED, TaskStatus.PENDING));
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.FAILED, TaskStatus.RETRYING));
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.FAILED, TaskStatus.COMPLETED));
    }

    @Test
    @DisplayName("防止状态回退")
    void testPreventStateRollback() {
        // 不能从 CRAWLED 回退到 CRAWLING
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.CRAWLED, TaskStatus.CRAWLING));

        // 不能从 AUDITING 回退到 CRAWLED
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.AUDITING, TaskStatus.CRAWLED));

        // 不能从 COMPLETED 回退
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.COMPLETED, TaskStatus.AUDITING));
    }

    @Test
    @DisplayName("不能跳过必要步骤")
    void testCannotSkipSteps() {
        // 不能从 PENDING 直接到 AUDITING（必须先爬取）
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.PENDING, TaskStatus.AUDITING));

        // 不能从 PENDING 直接到 COMPLETED
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.PENDING, TaskStatus.COMPLETED));

        // 不能从 CRAWLING 直接到 COMPLETED
        assertFalse(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.COMPLETED));
    }

    @Test
    @DisplayName("无效状态名称")
    void testInvalidStatusName() {
        assertFalse(TaskStatus.isAllowedTransition("INVALID_STATUS", "PENDING"));
        assertFalse(TaskStatus.isAllowedTransition("PENDING", "INVALID_STATUS"));
        assertFalse(TaskStatus.isAllowedTransition("INVALID", "ANOTHER_INVALID"));
    }

    @Test
    @DisplayName("多次重试场景")
    void testMultipleRetries() {
        // 第一次重试
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.RETRYING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.RETRYING, TaskStatus.CRAWLING));

        // 第二次重试
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.RETRYING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.RETRYING, TaskStatus.CRAWLING));

        // 最终成功
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.CRAWLING, TaskStatus.CRAWLED));
    }

    @Test
    @DisplayName("审核阶段重试")
    void testAuditStageRetry() {
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.AUDITING, TaskStatus.RETRYING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.RETRYING, TaskStatus.AUDITING));
        assertTrue(TaskStatus.isAllowedTransition(TaskStatus.AUDITING, TaskStatus.COMPLETED));
    }
}
