package com.xhs.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xhs.audit.model.dto.AuditRuleConfigRequest;
import com.xhs.audit.model.dto.AuditRuleConfigResponse;
import com.xhs.audit.model.entity.AuditRuleConfig;
import com.xhs.audit.service.AuditRuleConfigService;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleConfigController 单元测试")
class RuleConfigControllerTest {

    @Mock
    private AuditRuleConfigService ruleConfigService;

    @InjectMocks
    private RuleConfigController ruleConfigController;

    private AuditRuleConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of("天籁", "轩逸", "N7"))
                .excludedTags(List.of("东风日产", "尽兴由NI"))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("获取规则配置 - 成功")
    void testGetRuleConfig_Success() {
        // Given
        when(ruleConfigService.getConfig()).thenReturn(testConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.getRuleConfig();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMinTextLength()).isEqualTo(25);
        assertThat(response.getMinImageCount()).isEqualTo(1);
        assertThat(response.getRequiredTags()).containsExactly("东风日产", "尽兴由NI");
        assertThat(response.getCarModelNames()).containsExactly("天籁", "轩逸", "N7");
        assertThat(response.getExcludedTags()).containsExactly("东风日产", "尽兴由NI");
        assertThat(response.getUpdatedAt()).isNotNull();

        verify(ruleConfigService).getConfig();
    }

    @Test
    @DisplayName("获取规则配置 - 验证响应结构")
    void testGetRuleConfig_VerifyResponseStructure() {
        // Given
        when(ruleConfigService.getConfig()).thenReturn(testConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.getRuleConfig();

        // Then - 验证所有字段
        assertThat(response.getMinTextLength()).isNotNull();
        assertThat(response.getMinImageCount()).isNotNull();
        assertThat(response.getRequiredTags()).isNotNull();
        assertThat(response.getCarModelNames()).isNotNull();
        assertThat(response.getExcludedTags()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("更新规则配置 - 成功")
    void testUpdateRuleConfig_Success() {
        // Given
        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(50);
        request.setMinImageCount(2);
        request.setRequiredTags(List.of("品牌A", "品牌B"));
        request.setCarModelNames(List.of("车型X", "车型Y"));
        request.setExcludedTags(List.of("排除词"));

        AuditRuleConfig updatedConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(50)
                .minImageCount(2)
                .requiredTags(List.of("品牌A", "品牌B"))
                .carModelNames(List.of("车型X", "车型Y"))
                .excludedTags(List.of("排除词"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(ruleConfigService.updateConfig(request)).thenReturn(updatedConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.updateRuleConfig(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMinTextLength()).isEqualTo(50);
        assertThat(response.getMinImageCount()).isEqualTo(2);
        assertThat(response.getRequiredTags()).containsExactly("品牌A", "品牌B");
        assertThat(response.getCarModelNames()).containsExactly("车型X", "车型Y");
        assertThat(response.getExcludedTags()).containsExactly("排除词");

        verify(ruleConfigService).updateConfig(request);
    }

    @Test
    @DisplayName("更新规则配置 - 包含带#号的标签")
    void testUpdateRuleConfig_WithHashTags() {
        // Given
        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(25);
        request.setMinImageCount(1);
        request.setRequiredTags(List.of("#东风日产", "#尽兴由NI"));
        request.setCarModelNames(List.of("#天籁", "#轩逸"));
        request.setExcludedTags(List.of("#东风日产"));

        AuditRuleConfig updatedConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of("天籁", "轩逸"))
                .excludedTags(List.of("东风日产"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(ruleConfigService.updateConfig(request)).thenReturn(updatedConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.updateRuleConfig(request);

        // Then - 标签应该被清理（去除#号）
        assertThat(response.getRequiredTags()).doesNotContain("#");
        assertThat(response.getCarModelNames()).doesNotContain("#");
        assertThat(response.getExcludedTags()).doesNotContain("#");
    }

    @Test
    @DisplayName("更新规则配置 - 空列表")
    void testUpdateRuleConfig_EmptyLists() {
        // Given
        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(10);
        request.setMinImageCount(0);
        request.setRequiredTags(List.of());
        request.setCarModelNames(List.of());
        request.setExcludedTags(List.of());

        AuditRuleConfig updatedConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(10)
                .minImageCount(0)
                .requiredTags(List.of())
                .carModelNames(List.of())
                .excludedTags(List.of())
                .updatedAt(LocalDateTime.now())
                .build();

        when(ruleConfigService.updateConfig(request)).thenReturn(updatedConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.updateRuleConfig(request);

        // Then
        assertThat(response.getRequiredTags()).isEmpty();
        assertThat(response.getCarModelNames()).isEmpty();
        assertThat(response.getExcludedTags()).isEmpty();
    }

    @Test
    @DisplayName("更新规则配置 - 包含空格的标签")
    void testUpdateRuleConfig_WithWhitespaceTags() {
        // Given
        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(25);
        request.setMinImageCount(1);
        request.setRequiredTags(List.of("  东风日产  ", "  尽兴由NI  "));
        request.setCarModelNames(List.of("  天籁  ", "  轩逸  "));
        request.setExcludedTags(List.of("  东风日产  "));

        AuditRuleConfig updatedConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of("天籁", "轩逸"))
                .excludedTags(List.of("东风日产"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(ruleConfigService.updateConfig(request)).thenReturn(updatedConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.updateRuleConfig(request);

        // Then - 空格应该被去除
        assertThat(response.getRequiredTags()).allMatch(tag -> !tag.startsWith(" ") && !tag.endsWith(" "));
        assertThat(response.getCarModelNames()).allMatch(tag -> !tag.startsWith(" ") && !tag.endsWith(" "));
    }

    @Test
    @DisplayName("更新规则配置 - 包含重复标签")
    void testUpdateRuleConfig_WithDuplicateTags() {
        // Given
        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(25);
        request.setMinImageCount(1);
        request.setRequiredTags(List.of("东风日产", "东风日产", "尽兴由NI", "尽兴由NI"));
        request.setCarModelNames(List.of("天籁", "天籁", "轩逸"));
        request.setExcludedTags(List.of("东风日产", "东风日产"));

        AuditRuleConfig updatedConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of("天籁", "轩逸"))
                .excludedTags(List.of("东风日产"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(ruleConfigService.updateConfig(request)).thenReturn(updatedConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.updateRuleConfig(request);

        // Then - 重复标签应该被去除
        assertThat(response.getRequiredTags()).hasSize(2);
        assertThat(response.getCarModelNames()).hasSize(2);
        assertThat(response.getExcludedTags()).hasSize(1);
    }

    @Test
    @DisplayName("更新规则配置 - 验证更新时间")
    void testUpdateRuleConfig_VerifyUpdatedAt() {
        // Given
        LocalDateTime beforeUpdate = LocalDateTime.now().minusMinutes(1);

        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(30);
        request.setMinImageCount(2);
        request.setRequiredTags(List.of("品牌A"));
        request.setCarModelNames(List.of("车型A"));
        request.setExcludedTags(List.of("排除词"));

        AuditRuleConfig updatedConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(30)
                .minImageCount(2)
                .requiredTags(List.of("品牌A"))
                .carModelNames(List.of("车型A"))
                .excludedTags(List.of("排除词"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(ruleConfigService.updateConfig(request)).thenReturn(updatedConfig);

        // When
        AuditRuleConfigResponse response = ruleConfigController.updateRuleConfig(request);

        // Then - 更新时间应该更新
        assertThat(response.getUpdatedAt()).isAfter(beforeUpdate);
    }
}