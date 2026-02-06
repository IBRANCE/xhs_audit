package com.xhs.audit.util;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.AuditResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditResultConverter 单元测试
 * 测试审核结果转换功能
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@DisplayName("AuditResultConverter - 审核结果转换工具测试")
class AuditResultConverterTest {

    @Nested
    @DisplayName("toDecision 方法 - 正常场景")
    class ToDecisionHappyPath {

        @Test
        @DisplayName("完整 AuditResult 转换为 AuditDecision")
        void testToDecisionComplete() {
            AuditResult result = AuditResult.builder()
                    .id(1L)
                    .postId("test-post-123")
                    .url("https://www.xiaohongshu.com/explore/test-post-123")
                    .auditStatus("PASSED")
                    .confidenceScore(new BigDecimal("0.95"))
                    .modelName("test-model")
                    .auditedAt(LocalDateTime.of(2026, 1, 29, 12, 0, 0))
                    .build();

            AuditDecision decision = AuditResultConverter.toDecision(result);

            assertNotNull(decision);
            assertEquals("test-post-123", decision.getPostId());
            assertEquals("https://www.xiaohongshu.com/explore/test-post-123", decision.getUrl());
            assertEquals("PASSED", decision.getStatus());
            assertEquals(0.95, decision.getConfidenceScore(), 0.001);
            assertEquals("test-model", decision.getModelName());
            assertEquals(LocalDateTime.of(2026, 1, 29, 12, 0, 0), decision.getAuditedTime());
        }

        @Test
        @DisplayName("带 reasons 的 AuditResult 转换")
        void testToDecisionWithReasons() {
            List<Map<String, Object>> reasons = new ArrayList<>();
            Map<String, Object> reason1 = new HashMap<>();
            reason1.put("dimension", "title");
            reason1.put("reason", "包含敏感词");
            reason1.put("severity", "HIGH");
            reasons.add(reason1);

            AuditResult result = AuditResult.builder()
                    .id(1L)
                    .postId("test-post-123")
                    .url("https://www.xiaohongshu.com/explore/test-post-123")
                    .auditStatus("REJECTED")
                    .reasons(reasons)
                    .confidenceScore(new BigDecimal("0.85"))
                    .build();

            AuditDecision decision = AuditResultConverter.toDecision(result);

            assertNotNull(decision);
            assertEquals("REJECTED", decision.getStatus());
            assertNotNull(decision.getReasons());
            assertEquals(1, decision.getReasons().size());
            assertEquals("title", decision.getReasons().get(0).getDimension());
            assertEquals("包含敏感词", decision.getReasons().get(0).getReason());
            assertEquals("HIGH", decision.getReasons().get(0).getSeverity());
        }
    }

    @Nested
    @DisplayName("toDecision 方法 - 边界场景")
    class ToDecisionEdgeCases {

        @Test
        @DisplayName("null 输入应返回 null")
        void testToDecisionNull() {
            AuditDecision result = AuditResultConverter.toDecision(null);
            assertNull(result);
        }

        @Test
        @DisplayName("null confidenceScore 应返回 0.0")
        void testToDecisionNullConfidenceScore() {
            AuditResult result = AuditResult.builder()
                    .id(1L)
                    .postId("test-post-123")
                    .url("https://www.xiaohongshu.com/explore/test-post-123")
                    .auditStatus("PASSED")
                    .confidenceScore(null)
                    .build();

            AuditDecision decision = AuditResultConverter.toDecision(result);

            assertNotNull(decision);
            assertEquals(0.0, decision.getConfidenceScore(), 0.001);
        }

        @Test
        @DisplayName("空 reasons 列表应正常转换")
        void testToDecisionEmptyReasons() {
            AuditResult result = AuditResult.builder()
                    .id(1L)
                    .postId("test-post-123")
                    .url("https://www.xiaohongshu.com/explore/test-post-123")
                    .auditStatus("PASSED")
                    .reasons(new ArrayList<>())
                    .confidenceScore(new BigDecimal("0.90"))
                    .build();

            AuditDecision decision = AuditResultConverter.toDecision(result);

            assertNotNull(decision);
            assertNotNull(decision.getReasons());
            assertTrue(decision.getReasons().isEmpty());
        }
    }

    @Nested
    @DisplayName("convertListToReasons 方法 - 列表转换")
    class ConvertListToReasonsTests {

        @Test
        @DisplayName("完整列表转换")
        void testConvertListToReasonsComplete() {
            List<Map<String, Object>> list = new ArrayList<>();
            Map<String, Object> reason1 = new HashMap<>();
            reason1.put("dimension", "title");
            reason1.put("reason", "标题违规");
            reason1.put("severity", "MEDIUM");
            list.add(reason1);

            Map<String, Object> reason2 = new HashMap<>();
            reason2.put("dimension", "content");
            reason2.put("reason", "内容包含敏感信息");
            reason2.put("severity", "HIGH");
            list.add(reason2);

            List<AuditDecision.RejectReason> result = AuditResultConverter.convertListToReasons(list);

            assertNotNull(result);
            assertEquals(2, result.size());

            assertEquals("title", result.get(0).getDimension());
            assertEquals("标题违规", result.get(0).getReason());
            assertEquals("MEDIUM", result.get(0).getSeverity());

            assertEquals("content", result.get(1).getDimension());
            assertEquals("内容包含敏感信息", result.get(1).getReason());
            assertEquals("HIGH", result.get(1).getSeverity());
        }

        @Test
        @DisplayName("null 输入应返回空列表")
        void testConvertListToReasonsNull() {
            List<AuditDecision.RejectReason> result = AuditResultConverter.convertListToReasons(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空列表输入应返回空列表")
        void testConvertListToReasonsEmpty() {
            List<AuditDecision.RejectReason> result = AuditResultConverter.convertListToReasons(new ArrayList<>());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("reasonsToList 方法 - 原因转列表")
    class ReasonsToListTests {

        @Test
        @DisplayName("完整 RejectReason 列表转换")
        void testReasonsToListComplete() {
            List<AuditDecision.RejectReason> reasons = new ArrayList<>();
            reasons.add(new AuditDecision.RejectReason("title", "标题违规", "MEDIUM"));
            reasons.add(new AuditDecision.RejectReason("content", "内容违规", "HIGH"));

            List<Map<String, Object>> result = AuditResultConverter.reasonsToList(reasons);

            assertNotNull(result);
            assertEquals(2, result.size());

            Map<String, Object> first = result.get(0);
            assertEquals("title", first.get("dimension"));
            assertEquals("标题违规", first.get("reason"));
            assertEquals("MEDIUM", first.get("severity"));

            Map<String, Object> second = result.get(1);
            assertEquals("content", second.get("dimension"));
            assertEquals("内容违规", second.get("reason"));
            assertEquals("HIGH", second.get("severity"));
        }

        @Test
        @DisplayName("null 输入应返回空列表")
        void testReasonsToListNull() {
            List<Map<String, Object>> result = AuditResultConverter.reasonsToList(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空列表输入应返回空列表")
        void testReasonsToListEmpty() {
            List<Map<String, Object>> result = AuditResultConverter.reasonsToList(new ArrayList<>());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("单个 RejectReason 转换")
        void testReasonsToListSingle() {
            List<AuditDecision.RejectReason> reasons = new ArrayList<>();
            reasons.add(new AuditDecision.RejectReason("image", "图片包含敏感内容", "CRITICAL"));

            List<Map<String, Object>> result = AuditResultConverter.reasonsToList(reasons);

            assertNotNull(result);
            assertEquals(1, result.size());

            Map<String, Object> map = result.get(0);
            assertEquals("image", map.get("dimension"));
            assertEquals("图片包含敏感内容", map.get("reason"));
            assertEquals("CRITICAL", map.get("severity"));
        }
    }

    @Nested
    @DisplayName("完整流程测试")
    class FullFlowTests {

        @Test
        @DisplayName("AuditResult -> AuditDecision -> reasonsToList 完整转换")
        void testFullConversionFlow() {
            // 准备 AuditResult
            List<Map<String, Object>> reasons = new ArrayList<>();
            Map<String, Object> reason = new HashMap<>();
            reason.put("dimension", "content");
            reason.put("reason", "内容包含广告信息");
            reason.put("severity", "LOW");
            reasons.add(reason);

            AuditResult result = AuditResult.builder()
                    .id(1L)
                    .postId("flow-test-123")
                    .url("https://www.xiaohongshu.com/explore/flow-test-123")
                    .auditStatus("REJECTED")
                    .reasons(reasons)
                    .confidenceScore(new BigDecimal("0.75"))
                    .modelName("test-model")
                    .auditedAt(LocalDateTime.now())
                    .build();

            // 转换为 AuditDecision
            AuditDecision decision = AuditResultConverter.toDecision(result);
            assertNotNull(decision);
            assertEquals("REJECTED", decision.getStatus());
            assertEquals(1, decision.getReasons().size());

            // 转回 List<Map>
            List<Map<String, Object>> backToList = AuditResultConverter.reasonsToList(decision.getReasons());
            assertNotNull(backToList);
            assertEquals(1, backToList.size());
            assertEquals("content", backToList.get(0).get("dimension"));
            assertEquals("内容包含广告信息", backToList.get(0).get("reason"));
        }

        @Test
        @DisplayName("空结果转换验证")
        void testEmptyResultsConversion() {
            AuditResult result = AuditResult.builder()
                    .id(1L)
                    .postId("empty-test-123")
                    .url("https://www.xiaohongshu.com/explore/empty-test-123")
                    .auditStatus("PASSED")
                    .reasons(new ArrayList<>())
                    .confidenceScore(new BigDecimal("1.00"))
                    .modelName("test-model")
                    .auditedAt(LocalDateTime.now())
                    .build();

            AuditDecision decision = AuditResultConverter.toDecision(result);

            assertNotNull(decision);
            assertEquals("PASSED", decision.getStatus());
            assertNotNull(decision.getReasons());
            assertTrue(decision.getReasons().isEmpty());

            List<Map<String, Object>> backToList = AuditResultConverter.reasonsToList(decision.getReasons());
            assertNotNull(backToList);
            assertTrue(backToList.isEmpty());
        }

        @Test
        @DisplayName("所有状态类型转换验证")
        void testAllStatusTypesConversion() {
            String[] statuses = {"PASSED", "REJECTED", "UNCERTAIN"};

            for (String status : statuses) {
                AuditResult result = AuditResult.builder()
                        .id(1L)
                        .postId("status-test-123")
                        .url("https://www.xiaohongshu.com/explore/status-test-123")
                        .auditStatus(status)
                        .confidenceScore(new BigDecimal("0.80"))
                        .build();

                AuditDecision decision = AuditResultConverter.toDecision(result);

                assertNotNull(decision);
                assertEquals(status, decision.getStatus());
            }
        }
    }
}
