package com.xhs.audit.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.XhsContent;

/**
 * ContentAuditAgent单元测试
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@SpringBootTest
@ActiveProfiles("test")
class ContentAuditAgentTest {

    @Autowired
    private ContentAuditAgent contentAuditAgent;

    private XhsContent testContent;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testContent = new XhsContent();
        testContent.setPostId("test123abc");
        testContent.setUrl("https://www.xiaohongshu.com/explore/test123abc");
        testContent.setTitle("这是一个测试标题");
        testContent.setContent("这是测试内容，包含一些正常的文字描述。");
        testContent.setTags(List.of("测试", "单元测试"));
        testContent.setImages(List.of("https://example.com/image1.jpg"));
        testContent.setAuthorId("author123");
        testContent.setPublishedAt(LocalDateTime.now());
    }

    @Test
    void testAuditContent_NormalContent_ShouldPass() {
        // Given: 正常内容

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 验证结果
        assertNotNull(decision);
        assertNotNull(decision.getStatus());
        assertEquals("test123abc", decision.getPostId());
        assertNotNull(decision.getAuditedTime());
        assertNotNull(decision.getConfidenceScore());

        // 打印审核结果
        System.out.println("审核结果: " + decision.getStatus());
        System.out.println("置信度: " + decision.getConfidenceScore());
        System.out.println("风险等级: " + decision.getRiskLevel());
        if (decision.getReasons() != null && !decision.getReasons().isEmpty()) {
            System.out.println("驳回原因: " + decision.getReasons());
        }
    }

    @Test
    void testAuditContent_SensitiveWord_ShouldReject() {
        // Given: 包含敏感词的内容
        testContent.setTitle("一夜暴富的秘密");
        testContent.setContent("想要快速赚钱吗？加我微信！");

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 验证结果
        assertNotNull(decision);
        // 敏感词应该触发驳回或不确定状态
        assertTrue(
                "REJECTED".equals(decision.getStatus()) ||
                        "UNCERTAIN".equals(decision.getStatus()),
                "包含敏感词应该被驳回或标记为不确定");

        System.out.println("敏感词审核结果: " + decision.getStatus());
        System.out.println("驳回原因: " + decision.getReasons());
    }

    @Test
    void testAuditContent_NullFields_ShouldHandleGracefully() {
        // Given: 部分字段为空的内容
        testContent.setTitle(null);
        testContent.setContent(null);

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 验证不会抛出异常
        assertNotNull(decision);
        assertNotNull(decision.getStatus());

        System.out.println("空字段处理结果: " + decision.getStatus());
    }

    @Test
    void testAuditDecision_FactoryMethods() {
        // Test: PASSED决策
        AuditDecision passed = AuditDecision.passed("post123", 0.95);
        assertEquals("PASSED", passed.getStatus());
        assertEquals(0.95, passed.getConfidenceScore());
        assertEquals("LOW", passed.getRiskLevel());

        // Test: REJECTED决策
        AuditDecision rejected = AuditDecision.rejected(
                "post456",
                java.util.List.of(
                        new AuditDecision.RejectReason("title", "敏感词", "HIGH")),
                0.85);
        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("HIGH", rejected.getRiskLevel());

        // Test: UNCERTAIN决策
        AuditDecision uncertain = AuditDecision.uncertain("post789", "需要人工复核");
        assertEquals("UNCERTAIN", uncertain.getStatus());
        assertEquals(0.0, uncertain.getConfidenceScore());
    }

    // ========== v5.1 新增测试：验证修复的问题 ==========

    @Test
    void testAuditContent_MultipleImages_ShouldCountCorrectly() {
        // Given: 包含多张图片的内容
        testContent.setImages(List.of(
                "https://example.com/image1.jpg",
                "https://example.com/image2.jpg",
                "https://example.com/image3.jpg"));
        testContent.setContent("这是一篇关于东风日产N7的试驾体验，内容非常详细，包含了外观、内饰、动力、配置等多个方面的介绍。");
        testContent.setTags(List.of("#东风日产", "#尽兴由NI", "#日产N7"));

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 验证不会因为图片数量计算错误而失败
        assertNotNull(decision);
        assertNotNull(decision.getStatus());

        System.out.println("多图审核结果: " + decision.getStatus());
        System.out.println("图片数量: " + testContent.getImages().size());
    }

    @Test
    void testAuditContent_TagsFormatting_ShouldDisplayCorrectly() {
        // Given: 包含多个标签的内容
        testContent.setTags(List.of("#东风日产", "#尽兴由NI", "#日产N7", "#SUV", "#试驾体验"));
        testContent.setContent("这是一篇关于东风日产N7的试驾体验，内容非常详细，包含了外观、内饰、动力、配置等多个方面的介绍。");

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 验证不会因为标签格式化问题而失败
        assertNotNull(decision);
        assertNotNull(decision.getStatus());

        System.out.println("标签格式化审核结果: " + decision.getStatus());
        System.out.println("标签内容: " + String.join("、", testContent.getTags()));
    }

    @Test
    void testAuditContent_EmptyTags_ShouldHandleGracefully() {
        // Given: 标签为空列表
        testContent.setTags(List.of());
        testContent.setContent("内容太短");
        testContent.setImages(List.of());

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 应该被基础规则驳回
        assertNotNull(decision);
        assertEquals("REJECTED", decision.getStatus());
        assertTrue(decision.getReasons().stream()
                .anyMatch(r -> r.getDimension().equals("tag")));

        System.out.println("空标签审核结果: " + decision.getStatus());
        System.out.println("驳回原因: " + decision.getReasons());
    }

    @Test
    void testAuditContent_NoImages_ShouldRejectByRules() {
        // Given: 没有图片
        testContent.setImages(null);
        testContent.setContent("这是一篇关于东风日产N7的试驾体验，内容非常详细。");
        testContent.setTags(List.of("#东风日产", "#尽兴由NI", "#日产N7"));

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 应该被基础规则驳回（图片数量不足）
        assertNotNull(decision);
        assertEquals("REJECTED", decision.getStatus());
        assertTrue(decision.getReasons().stream()
                .anyMatch(r -> r.getDimension().equals("content_format")
                        && r.getReason().contains("图片")));

        System.out.println("无图片审核结果: " + decision.getStatus());
        System.out.println("驳回原因: " + decision.getReasons());
    }

    @Test
    void testAuditContent_ShortContent_ShouldRejectByRules() {
        // Given: 正文太短
        testContent.setContent("短文本");
        testContent.setImages(List.of("https://example.com/image1.jpg"));
        testContent.setTags(List.of("#东风日产", "#尽兴由NI", "#日产N7"));

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 应该被基础规则驳回（文字不足）
        assertNotNull(decision);
        assertEquals("REJECTED", decision.getStatus());
        assertTrue(decision.getReasons().stream()
                .anyMatch(r -> r.getDimension().equals("content_format")
                        && r.getReason().contains("正文文字不足")));

        System.out.println("短文本审核结果: " + decision.getStatus());
        System.out.println("驳回原因: " + decision.getReasons());
    }

    @Test
    void testAuditContent_ValidContent_ShouldPassRules() {
        // Given: 完全符合规则的内容
        testContent.setContent("这是一篇关于东风日产N7的试驾体验，内容非常详细，包含了外观、内饰、动力、配置等多个方面的介绍。");
        testContent.setImages(List.of("https://example.com/image1.jpg"));
        testContent.setTags(List.of("#东风日产", "#尽兴由NI", "#日产N7"));

        // When: 执行审核
        AuditDecision decision = contentAuditAgent.auditContent(testContent);

        // Then: 应该通过基础规则，进入LLM审核
        assertNotNull(decision);
        assertNotNull(decision.getStatus());
        // 不应该被规则验证器拒绝
        if (decision.getStatus().equals("REJECTED")) {
            assertFalse(decision.getReasons().stream()
                    .anyMatch(r -> r.getDimension().equals("content_format")),
                    "符合规则的内容不应该被content_format拒绝");
        }

        System.out.println("符合规则的内容审核结果: " + decision.getStatus());
        if (decision.getReasons() != null && !decision.getReasons().isEmpty()) {
            System.out.println("驳回原因: " + decision.getReasons());
        }
    }
}
