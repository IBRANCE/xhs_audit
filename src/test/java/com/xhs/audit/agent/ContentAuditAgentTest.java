package com.xhs.audit.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.agent.ContentAuditAgent;
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
}
