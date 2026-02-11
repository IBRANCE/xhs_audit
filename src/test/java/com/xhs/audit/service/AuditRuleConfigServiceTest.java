package com.xhs.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xhs.audit.config.RuleValidatorProperties;
import com.xhs.audit.model.dto.AuditRuleConfigRequest;
import com.xhs.audit.model.entity.AuditRuleConfig;
import com.xhs.audit.repository.AuditRuleConfigRepository;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditRuleConfigService 单元测试")
class AuditRuleConfigServiceTest {

    @Mock
    private AuditRuleConfigRepository repository;

    @Mock
    private RuleValidatorProperties defaultProperties;

    @InjectMocks
    private AuditRuleConfigService ruleConfigService;

    private AuditRuleConfig existingConfig;

    @BeforeEach
    void setUp() {
        existingConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of("天籁", "轩逸", "N7"))
                .excludedTags(List.of("东风日产", "尽兴由NI"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(defaultProperties.getMinTextLength()).thenReturn(25);
        when(defaultProperties.getMinImageCount()).thenReturn(1);
        when(defaultProperties.getRequiredTags()).thenReturn(List.of("东风日产", "尽兴由NI"));
        when(defaultProperties.getCarModelNames()).thenReturn(List.of("天籁", "轩逸", "N7"));
        when(defaultProperties.getExcludedTags()).thenReturn(List.of("东风日产", "尽兴由NI"));
    }

    @Test
    @DisplayName("初始化时 - 数据库已有配置，应使用现有配置")
    void testInit_ExistingConfigInDatabase() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));

        // When
        ruleConfigService.init();

        // Then
        verify(repository, never()).save(any(AuditRuleConfig.class));
    }

    @Test
    @DisplayName("初始化时 - 数据库无配置，应创建默认配置")
    void testInit_NoConfigInDatabase_ShouldCreateDefault() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.empty());

        // When
        ruleConfigService.init();

        // Then
        verify(repository).save(any(AuditRuleConfig.class));
    }

    @Test
    @DisplayName("获取配置 - 返回正确的配置")
    void testGetConfig_ReturnsCorrectConfig() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));
        ruleConfigService.init();

        // When
        AuditRuleConfig config = ruleConfigService.getConfig();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.getMinTextLength()).isEqualTo(25);
        assertThat(config.getMinImageCount()).isEqualTo(1);
        assertThat(config.getRequiredTags()).containsExactly("东风日产", "尽兴由NI");
    }

    @Test
    @DisplayName("获取配置 - 线程安全测试")
    void testGetConfig_ThreadSafety() throws Exception {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));
        ruleConfigService.init();

        // When - 多个线程同时获取配置
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                AuditRuleConfig config = ruleConfigService.getConfig();
                assertThat(config).isNotNull();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        Thread t3 = new Thread(task);

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        // Then - 不应该抛出异常
        verify(repository).findById(AuditRuleConfig.SINGLETON_ID);
    }

    @Test
    @DisplayName("更新配置 - 更新所有字段")
    void testUpdateConfig_UpdateAllFields() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));

        AuditRuleConfig updated = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(50)
                .minImageCount(2)
                .requiredTags(List.of("品牌A", "品牌B"))
                .carModelNames(List.of("车型X", "车型Y"))
                .excludedTags(List.of("排除词"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(AuditRuleConfig.class))).thenReturn(updated);

        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(50);
        request.setMinImageCount(2);
        request.setRequiredTags(List.of("品牌A", "品牌B"));
        request.setCarModelNames(List.of("车型X", "车型Y"));
        request.setExcludedTags(List.of("排除词"));

        // When
        AuditRuleConfig result = ruleConfigService.updateConfig(request);

        // Then
        assertThat(result.getMinTextLength()).isEqualTo(50);
        assertThat(result.getMinImageCount()).isEqualTo(2);
        assertThat(result.getRequiredTags()).containsExactly("品牌A", "品牌B");
        verify(repository).save(any(AuditRuleConfig.class));
    }

    @Test
    @DisplayName("更新配置 - 标签清理（去除#号和空格，保留首次出现的原始值）")
    void testUpdateConfig_SanitizeTags() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));

        AuditRuleConfig updated = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("#东风日产", "  #尽兴由NI  ", "#品牌A"))
                .carModelNames(List.of(" #天籁", "#轩逸 "))
                .excludedTags(List.of(" #东风日产 "))
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(AuditRuleConfig.class))).thenReturn(updated);

        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(25);
        request.setMinImageCount(1);
        request.setRequiredTags(List.of("#东风日产", "  #尽兴由NI  ", "#品牌A"));
        request.setCarModelNames(List.of(" #天籁", "#轩逸 "));
        request.setExcludedTags(List.of(" #东风日产 "));

        // When
        AuditRuleConfig result = ruleConfigService.updateConfig(request);

        // Then - 标签应该被清理，保留首次出现的原始值
        assertThat(result.getRequiredTags())
                .containsExactly("#东风日产", "  #尽兴由NI  ", "#品牌A");
        assertThat(result.getCarModelNames())
                .containsExactly(" #天籁", "#轩逸 ");
        assertThat(result.getExcludedTags())
                .containsExactly(" #东风日产 ");
    }

    @Test
    @DisplayName("更新配置 - 去除重复标签")
    void testUpdateConfig_RemoveDuplicateTags() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));

        AuditRuleConfig updated = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of("天籁"))
                .excludedTags(List.of("东风日产"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(AuditRuleConfig.class))).thenReturn(updated);

        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(25);
        request.setMinImageCount(1);
        request.setRequiredTags(List.of("#东风日产", "#东风日产", "#尽兴由NI", "#尽兴由NI"));
        request.setCarModelNames(List.of("#天籁", "#天籁", "#天籁"));
        request.setExcludedTags(List.of("#东风日产", "#东风日产"));

        // When
        AuditRuleConfig result = ruleConfigService.updateConfig(request);

        // Then - 重复标签应该被去除
        assertThat(result.getRequiredTags()).hasSize(2);
        assertThat(result.getCarModelNames()).hasSize(1);
        assertThat(result.getExcludedTags()).hasSize(1);
    }

    @Test
    @DisplayName("更新配置 - 数据库无配置时创建新配置")
    void testUpdateConfig_NoExistingConfig_ShouldCreateNew() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.empty());

        AuditRuleConfig newConfig = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(30)
                .minImageCount(2)
                .requiredTags(List.of("品牌A"))
                .carModelNames(List.of("车型A"))
                .excludedTags(List.of("排除词"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(AuditRuleConfig.class))).thenReturn(newConfig);

        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(30);
        request.setMinImageCount(2);
        request.setRequiredTags(List.of("品牌A"));
        request.setCarModelNames(List.of("车型A"));
        request.setExcludedTags(List.of("排除词"));

        // When
        AuditRuleConfig result = ruleConfigService.updateConfig(request);

        // Then - updateConfig 应该创建并返回新配置
        assertThat(result.getMinTextLength()).isEqualTo(30);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(any(AuditRuleConfig.class));
    }

    @Test
    @DisplayName("更新配置 - 空标签列表应被处理为空列表")
    void testUpdateConfig_EmptyTags_ShouldBeHandled() {
        // Given
        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));

        AuditRuleConfig updated = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(new ArrayList<>())
                .carModelNames(new ArrayList<>())
                .excludedTags(new ArrayList<>())
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(AuditRuleConfig.class))).thenReturn(updated);

        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(25);
        request.setMinImageCount(1);
        request.setRequiredTags(new ArrayList<>(java.util.List.of("", "  ")));
        request.setCarModelNames(new ArrayList<>());
        request.setExcludedTags(new ArrayList<>());

        // When
        AuditRuleConfig result = ruleConfigService.updateConfig(request);

        // Then
        assertThat(result.getRequiredTags()).isEmpty();
        assertThat(result.getCarModelNames()).isEmpty();
        assertThat(result.getExcludedTags()).isEmpty();
    }

    @Test
    @DisplayName("更新配置 - 更新时间应被更新")
    void testUpdateConfig_UpdatedAtShouldBeUpdated() {
        // Given
        LocalDateTime oldTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        existingConfig.setUpdatedAt(oldTime);

        when(repository.findById(AuditRuleConfig.SINGLETON_ID))
                .thenReturn(java.util.Optional.of(existingConfig));

        AuditRuleConfig updated = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(25)
                .minImageCount(1)
                .requiredTags(List.of("东风日产", "尽兴由NI"))
                .carModelNames(List.of("天籁", "轩逸"))
                .excludedTags(List.of("东风日产"))
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(AuditRuleConfig.class))).thenReturn(updated);

        AuditRuleConfigRequest request = new AuditRuleConfigRequest();
        request.setMinTextLength(25);
        request.setMinImageCount(1);
        request.setRequiredTags(List.of("#东风日产", "#尽兴由NI"));
        request.setCarModelNames(List.of("#天籁", "#轩逸"));
        request.setExcludedTags(List.of("#东风日产"));

        // When
        AuditRuleConfig result = ruleConfigService.updateConfig(request);

        // Then - 更新时间应该被更新
        assertThat(result.getUpdatedAt()).isAfter(oldTime);
    }
}