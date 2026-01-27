package com.xhs.audit.function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xhs.audit.model.entity.AuditRule;
import com.xhs.audit.model.entity.SensitiveWord;
import com.xhs.audit.repository.AuditRuleRepository;
import com.xhs.audit.repository.SensitiveWordRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * AuditRuleFunctions Function工具测试
 * 测试LLM Agent可调用的工具函数
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditRuleFunctions - 审核规则工具函数测试")
class AuditRuleFunctionsTest {

    @Mock
    private AuditRuleRepository auditRuleRepository;

    @Mock
    private SensitiveWordRepository sensitiveWordRepository;

    @InjectMocks
    private AuditRuleFunctions auditRuleFunctions;

    private List<AuditRule> testRules;
    private List<SensitiveWord> testSensitiveWords;

    @BeforeEach
    void setUp() {
        // 初始化测试规则
        AuditRule rule1 = new AuditRule();
        rule1.setId(1L);
        rule1.setDimension("title");
        rule1.setRuleType("keyword");
        rule1.setRuleName("Title Contains Forbidden Keywords");
        rule1.setPriority(1);

        AuditRule rule2 = new AuditRule();
        rule2.setId(2L);
        rule2.setDimension("content");
        rule2.setRuleType("pattern");
        rule2.setRuleName("Content Pattern Violation");
        rule2.setPriority(2);

        testRules = Arrays.asList(rule1, rule2);

        // 初始化测试敏感词
        SensitiveWord word1 = new SensitiveWord();
        word1.setId(1L);
        word1.setWord("违禁词1");
        word1.setCategory("politics");
        word1.setSeverity("HIGH");

        SensitiveWord word2 = new SensitiveWord();
        word2.setId(2L);
        word2.setWord("敏感词2");
        word2.setCategory("violence");
        word2.setSeverity("CRITICAL");

        testSensitiveWords = Arrays.asList(word1, word2);
    }

    /**
     * 测试：获取指定维度的审核规则
     */
    @Test
    @DisplayName("获取审核规则 - 按维度(title)")
    void testGetAuditRulesByDimension() {
        // Arrange
        when(auditRuleRepository.findByDimensionAndEnabledTrue("title"))
                .thenReturn(Arrays.asList(testRules.get(0)));

        Function<AuditRuleFunctions.GetAuditRulesRequest, List<AuditRuleFunctions.AuditRuleDTO>> 
            getAuditRulesFunc = auditRuleFunctions.getAuditRules();

        // Act
        List<AuditRuleFunctions.AuditRuleDTO> result = getAuditRulesFunc.apply(
                new AuditRuleFunctions.GetAuditRulesRequest("title"));

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).dimension()).isEqualTo("title");

        log.info("✓ 获取按维度规则测试通过");
    }

    /**
     * 测试：获取所有审核规则
     */
    @Test
    @DisplayName("获取审核规则 - 获取全部规则(all)")
    void testGetAllAuditRules() {
        // Arrange
        when(auditRuleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(testRules);

        Function<AuditRuleFunctions.GetAuditRulesRequest, List<AuditRuleFunctions.AuditRuleDTO>> 
            getAuditRulesFunc = auditRuleFunctions.getAuditRules();

        // Act
        List<AuditRuleFunctions.AuditRuleDTO> result = getAuditRulesFunc.apply(
                new AuditRuleFunctions.GetAuditRulesRequest("all"));

        // Assert
        assertThat(result).hasSize(2);
        verify(auditRuleRepository).findByEnabledTrueOrderByPriorityAsc();

        log.info("✓ 获取全部规则测试通过");
    }

    /**
     * 测试：获取空规则列表
     */
    @Test
    @DisplayName("获取审核规则 - 无匹配规则返回空列表")
    void testGetAuditRulesEmpty() {
        // Arrange
        when(auditRuleRepository.findByDimensionAndEnabledTrue("unknown"))
                .thenReturn(Collections.emptyList());

        Function<AuditRuleFunctions.GetAuditRulesRequest, List<AuditRuleFunctions.AuditRuleDTO>> 
            getAuditRulesFunc = auditRuleFunctions.getAuditRules();

        // Act
        List<AuditRuleFunctions.AuditRuleDTO> result = getAuditRulesFunc.apply(
                new AuditRuleFunctions.GetAuditRulesRequest("unknown"));

        // Assert
        assertThat(result).isEmpty();

        log.info("✓ 空规则列表测试通过");
    }

    /**
     * 测试：检查敏感词 - 找到敏感词
     */
    @Test
    @DisplayName("检查敏感词 - 文本包含敏感词")
    void testCheckSensitiveWordsFound() {
        // Arrange
        when(sensitiveWordRepository.findByEnabledTrue())
                .thenReturn(testSensitiveWords);

        Function<AuditRuleFunctions.CheckSensitiveWordsRequest, AuditRuleFunctions.SensitiveWordResult> 
            checkFunc = auditRuleFunctions.checkSensitiveWords();

        // Act
        AuditRuleFunctions.SensitiveWordResult result = checkFunc.apply(
                new AuditRuleFunctions.CheckSensitiveWordsRequest("这是违禁词1和其他内容"));

        // Assert
        assertThat(result.found()).isTrue();
        assertThat(result.matches()).isNotEmpty();
        assertThat(result.highestSeverity()).isEqualTo("HIGH");

        log.info("✓ 检查敏感词发现测试通过");
    }

    /**
     * 测试：检查敏感词 - 未找到敏感词
     */
    @Test
    @DisplayName("检查敏感词 - 文本无敏感词")
    void testCheckSensitiveWordsNotFound() {
        // Arrange
        when(sensitiveWordRepository.findByEnabledTrue())
                .thenReturn(testSensitiveWords);

        Function<AuditRuleFunctions.CheckSensitiveWordsRequest, AuditRuleFunctions.SensitiveWordResult> 
            checkFunc = auditRuleFunctions.checkSensitiveWords();

        // Act
        AuditRuleFunctions.SensitiveWordResult result = checkFunc.apply(
                new AuditRuleFunctions.CheckSensitiveWordsRequest("这是安全的正常文本"));

        // Assert
        assertThat(result.found()).isFalse();
        assertThat(result.matches()).isEmpty();
        assertThat(result.highestSeverity()).isEqualTo("NONE");

        log.info("✓ 检查敏感词未发现测试通过");
    }

    /**
     * 测试：检查空文本
     */
    @Test
    @DisplayName("检查敏感词 - 空文本处理")
    void testCheckSensitiveWordsEmpty() {
        // Arrange
        Function<AuditRuleFunctions.CheckSensitiveWordsRequest, AuditRuleFunctions.SensitiveWordResult> 
            checkFunc = auditRuleFunctions.checkSensitiveWords();

        // Act
        AuditRuleFunctions.SensitiveWordResult result = checkFunc.apply(
                new AuditRuleFunctions.CheckSensitiveWordsRequest(""));

        // Assert
        assertThat(result.found()).isFalse();
        assertThat(result.matches()).isEmpty();

        log.info("✓ 空文本处理测试通过");
    }

    /**
     * 测试：多个敏感词检测 - 选择最高严重等级
     */
    @Test
    @DisplayName("检查敏感词 - 多词匹配选择最高等级")
    void testCheckSensitiveWordsMultipleMatches() {
        // Arrange
        when(sensitiveWordRepository.findByEnabledTrue())
                .thenReturn(testSensitiveWords);

        Function<AuditRuleFunctions.CheckSensitiveWordsRequest, AuditRuleFunctions.SensitiveWordResult> 
            checkFunc = auditRuleFunctions.checkSensitiveWords();

        // Act - 包含HIGH和CRITICAL两个敏感词
        AuditRuleFunctions.SensitiveWordResult result = checkFunc.apply(
                new AuditRuleFunctions.CheckSensitiveWordsRequest(
                        "包含违禁词1和敏感词2的文本"));

        // Assert
        assertThat(result.found()).isTrue();
        assertThat(result.matches()).hasSize(2);
        // 应该返回最高等级CRITICAL
        assertThat(result.highestSeverity()).isEqualTo("CRITICAL");

        log.info("✓ 多词匹配最高等级测试通过");
    }

    /**
     * 测试：敏感词严重程度排序
     */
    @Test
    @DisplayName("检查敏感词 - 严重程度：CRITICAL > HIGH > MEDIUM > LOW")
    void testSeveritySorting() {
        // Arrange
        SensitiveWord wordMedium = new SensitiveWord();
        wordMedium.setId(3L);
        wordMedium.setWord("中等词");
        wordMedium.setSeverity("MEDIUM");

        List<SensitiveWord> mixedSeverityWords = Arrays.asList(
                testSensitiveWords.get(0), // HIGH
                testSensitiveWords.get(1), // CRITICAL
                wordMedium                  // MEDIUM
        );

        when(sensitiveWordRepository.findByEnabledTrue())
                .thenReturn(mixedSeverityWords);

        Function<AuditRuleFunctions.CheckSensitiveWordsRequest, AuditRuleFunctions.SensitiveWordResult> 
            checkFunc = auditRuleFunctions.checkSensitiveWords();

        // Act
        AuditRuleFunctions.SensitiveWordResult result = checkFunc.apply(
                new AuditRuleFunctions.CheckSensitiveWordsRequest(
                        "包含敏感词2和中等词的文本"));

        // Assert
        // CRITICAL应被选为最高等级
        assertThat(result.highestSeverity()).isEqualTo("CRITICAL");

        log.info("✓ 严重程度排序测试通过");
    }

    /**
     * 测试：大文本敏感词检查（性能测试）
     */
    @Test
    @DisplayName("检查敏感词 - 大文本处理")
    void testCheckSensitiveWordsLargeText() {
        // Arrange
        when(sensitiveWordRepository.findByEnabledTrue())
                .thenReturn(testSensitiveWords);

        Function<AuditRuleFunctions.CheckSensitiveWordsRequest, AuditRuleFunctions.SensitiveWordResult> 
            checkFunc = auditRuleFunctions.checkSensitiveWords();

        // 构建大文本（10000字符）
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeText.append("这是一段普通的文本，不包含敏感内容。");
        }
        largeText.append("末尾包含违禁词1");

        // Act
        AuditRuleFunctions.SensitiveWordResult result = checkFunc.apply(
                new AuditRuleFunctions.CheckSensitiveWordsRequest(largeText.toString()));

        // Assert
        assertThat(result.found()).isTrue();
        assertThat(result.matches()).isNotEmpty();

        log.info("✓ 大文本处理测试通过: textLength={}", largeText.length());
    }

    /**
     * 测试：规则优先级排序
     */
    @Test
    @DisplayName("获取审核规则 - 按优先级排序")
    void testGetRulesByPriority() {
        // Arrange
        when(auditRuleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(testRules);

        Function<AuditRuleFunctions.GetAuditRulesRequest, List<AuditRuleFunctions.AuditRuleDTO>> 
            getAuditRulesFunc = auditRuleFunctions.getAuditRules();

        // Act
        List<AuditRuleFunctions.AuditRuleDTO> result = getAuditRulesFunc.apply(
                new AuditRuleFunctions.GetAuditRulesRequest("all"));

        // Assert
        assertThat(result).isSortedAccordingTo((r1, r2) -> 
                Integer.compare(r1.priority(), r2.priority()));

        log.info("✓ 规则优先级排序测试通过");
    }
}
