package com.xhs.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.AuditRuleConfig;
import com.xhs.audit.model.entity.XhsContent;

/**
 * 内容规则验证器测试
 */
@DisplayName("内容规则验证器测试")
class ContentRuleValidatorTest {

    private ContentRuleValidator validator;
    private AuditRuleConfigService ruleConfigService;

    @BeforeEach
    void setUp() {
        ruleConfigService = Mockito.mock(AuditRuleConfigService.class);

        AuditRuleConfig defaultConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of(
                        "天籁", "轩逸", "逍客", "奇骏", "X-TRAIL", "ARIYA", "艾睿雅", "N7", "N6", "NX8", "探陆",
                        "NISSAN",
                        "启辰大V", "启辰星", "启辰D60", "启辰", "Venucia",
                        "QX50", "QX60", "Q50L", "英菲尼迪", "INFINITI"))
                .excludedTags(List.of("东风日产", "尽兴由NI"))
                .updatedAt(LocalDateTime.now())
                .build();

        Mockito.when(ruleConfigService.getConfig()).thenReturn(defaultConfig);

        validator = new ContentRuleValidator(ruleConfigService);
    }

    @Test
    @DisplayName("规则验证通过 - 所有规则满足")
    void testValidateRules_Pass() {
        // Given
        XhsContent content = createValidContent();

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull(); // 规则通过返回null
    }

    @Test
    @DisplayName("规则验证失败 - 正文字数不足")
    void testValidateRules_TextTooShort() {
        // Given
        XhsContent content = createValidContent();
        content.setContent("这是一个短文本");

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getModelName()).isEqualTo("RuleValidator");
        assertThat(result.getConfidenceScore()).isEqualTo(1.0);
        assertThat(result.getReasons())
                .anyMatch(r -> r.getDimension().equals("content_format")
                        && r.getReason().contains("正文文字不足"));
    }

    @Test
    @DisplayName("规则验证失败 - 图片数量不足")
    void testValidateRules_NoImages() {
        // Given
        XhsContent content = createValidContent();
        content.setImages(List.of());

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getReasons())
                .anyMatch(r -> r.getDimension().equals("content_format")
                        && r.getReason().contains("图片数量不足"));
    }

    @Test
    @DisplayName("规则验证失败 - 未包含任何标签")
    void testValidateRules_NoTags() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(null);

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getReasons())
                .anyMatch(r -> r.getDimension().equals("tag")
                        && r.getReason().contains("未包含任何话题标签")
                        && r.getSeverity().equals("CRITICAL"));
    }

    @Test
    @DisplayName("规则验证失败 - 缺少必带标签")
    void testValidateRules_MissingRequiredTags() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(List.of("#汽车", "#推荐", "#日产N7"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getReasons())
                .anyMatch(r -> r.getDimension().equals("tag")
                        && r.getReason().contains("缺少必带话题标签"));
    }

    @Test
    @DisplayName("规则验证失败 - 缺少车型标签")
    void testValidateRules_MissingCarModelTag() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#汽车"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getReasons())
                .anyMatch(r -> r.getDimension().equals("tag")
                        && r.getReason().contains("缺少车型名称标签"));
    }

    @Test
    @DisplayName("规则验证通过 - 标签带#号")
    void testValidateRules_TagsWithHashSymbol() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#日产N7"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("规则验证通过 - 标签不带#号")
    void testValidateRules_TagsWithoutHashSymbol() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(List.of("东风日产", "尽兴由NI", "日产N7"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("规则验证通过 - 标签混合大小写")
    void testValidateRules_TagsMixedCase() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#NISSAN"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull(); // NISSAN是车型关键词，应该通过
    }

    @Test
    @DisplayName("规则验证通过 - 包含启辰车型")
    void testValidateRules_QichenCarModel() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#启辰大V"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("规则验证通过 - 包含英菲尼迪车型")
    void testValidateRules_InfinitiCarModel() {
        // Given
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#英菲尼迪QX50"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("规则验证失败 - 多个规则同时不满足")
    void testValidateRules_MultipleFailures() {
        // Given
        XhsContent content = XhsContent.builder()
                .postId("test123")
                .url("https://www.xiaohongshu.com/explore/test123")
                .title("测试标题")
                .content("短文本") // 字数不足
                .images(List.of()) // 无图片
                .tags(List.of("#汽车")) // 缺少必带标签
                .crawledAt(LocalDateTime.now())
                .build();

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        // 应该有至少3个驳回原因：字数不足、图片不足、缺少标签
        assertThat(result.getReasons()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("规则验证 - 正文恰好25字")
    void testValidateRules_ExactlyMinTextLength() {
        // Given
        XhsContent content = createValidContent();
        content.setContent("这是一篇关于东风日产的测试内容，字数刚好二十五个字。"); // 恰好25字

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull(); // 应该通过
    }

    @Test
    @DisplayName("规则验证失败 - 正文24字")
    void testValidateRules_OneLessThanMinTextLength() {
        // Given
        XhsContent content = createValidContent();
        content.setContent("这是一篇关于东风日产的测试内容字数二十四。"); // 只有24字

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("规则验证 - 正文包含空白符")
    void testValidateRules_TextWithWhitespace() {
        // Given
        XhsContent content = createValidContent();
        // 包含空格、换行的文本，去除后应该足够25字
        content.setContent("  这是一篇关于东风日产N7的试驾体验，\n内容非常详细，包含了多个方面。  \n");

        // When
        AuditDecision result = validator.validateRules(content);

        // Then
        assertThat(result).isNull(); // trim后应该通过
    }

    // ========== v5.1 新增测试：验证修复的问题 ==========

    @Test
    @DisplayName("修复验证 - 排除标签不应被当作车型标签")
    void testValidateRules_ExcludedTagsNotCountedAsCarModel() {
        // Given - 只有必带标签，没有真正的车型标签
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#汽车推广"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then - 应该失败，因为缺少车型标签
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getReasons())
                .anyMatch(r -> r.getDimension().equals("tag")
                        && r.getReason().contains("缺少车型名称标签"));
    }

    @Test
    @DisplayName("修复验证 - 车型标签精确匹配（避免过度匹配）")
    void testValidateRules_CarModelPreciseMatching() {
        // Given - "日产车主"不应该被匹配为"日产"车型
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#日产车主"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then - 应该失败，因为"日产车主"不是有效的车型标签
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getReasons())
                .anyMatch(r -> r.getDimension().equals("tag")
                        && r.getReason().contains("缺少车型名称标签"));
    }

    @Test
    @DisplayName("修复验证 - 车型标签开头匹配（应该通过）")
    void testValidateRules_CarModelStartsWith() {
        // Given - "天籁2024"应该被识别为天籁车型
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#天籁2024"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then - 应该通过
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("修复验证 - 车型标签结尾匹配（应该通过）")
    void testValidateRules_CarModelEndsWith() {
        // Given - "豪华版天籁"应该被识别为天籁车型
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#豪华版天籁"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then - 应该通过
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("修复验证 - 车型标签中间包含不应匹配")
    void testValidateRules_CarModelInMiddleNotMatched() {
        // Given - "我的天籁故事"中间包含天籁，但不应该匹配
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#我的天籁故事"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then - 应该失败
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("修复验证 - 精确匹配车型名")
    void testValidateRules_ExactCarModelMatch() {
        // Given - 精确匹配车型名
        XhsContent content = createValidContent();
        content.setTags(List.of("#东风日产", "#尽兴由NI", "#天籁"));

        // When
        AuditDecision result = validator.validateRules(content);

        // Then - 应该通过
        assertThat(result).isNull();
    }

    /**
     * 创建符合所有规则的内容
     */
    private XhsContent createValidContent() {
        return XhsContent.builder()
                .postId("test123456")
                .url("https://www.xiaohongshu.com/explore/test123456")
                .title("测试东风日产N7试驾体验")
                .content("这是一篇关于东风日产N7的试驾体验，内容非常详细，包含了外观、内饰、动力、配置等多个方面的介绍。") // 超过25字
                .images(List.of("https://example.com/image1.jpg")) // 1张图片
                .tags(List.of("#东风日产", "#尽兴由NI", "#日产N7")) // 所有必带标签
                .crawledAt(LocalDateTime.now())
                .build();
    }
}
