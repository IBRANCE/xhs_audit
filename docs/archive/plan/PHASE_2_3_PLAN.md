# Phase 2.3: AuditRuleFunctions - 详细规划

## 📋 阶段概述

**目标**: 实现Spring AI Function Calling工具类,定义审核规则和敏感词检查能力

**工作量**: 2天  
**完成日期**: 2026-02-05  
**关键交付**: AuditRuleFunctions.java + 单元测试 + 文档

---

## 🏗️ 架构设计

### Function Calling定义

将实现两个主要的Function供LLM调用:

```
┌─ AuditRuleFunctions (240行)
│  ├─ @Bean public Supplier<AuditRuleFunction> getAuditRules()
│  │  └─ 返回所有审核规则 (RuleType/Content/Severity)
│  │
│  ├─ @Bean public Supplier<SensitiveWordFunction> checkSensitiveWords()
│  │  └─ 检查敏感词库 (返回匹配列表)
│  │
│  └─ @Bean public Supplier<ContentAnalysisFunction> analyzeContent()
│     └─ 深度内容分析 (关键词/情感/风险)
│
└─ 支持类 (150行)
   ├─ AuditRuleFunction.java (DTO)
   ├─ SensitiveWordFunction.java (DTO)
   └─ ContentAnalysisFunction.java (DTO)
```

### 缓存策略

```
Caffeine Cache (内存)
├─ 规则缓存 (TTL: 1小时)
│  └─ Key: "rules:all"
│  └─ Value: List<AuditRule>
│
├─ 敏感词缓存 (TTL: 2小时)
│  └─ Key: "sensitive:words"
│  └─ Value: Set<String>
│
└─ 分析结果缓存 (TTL: 30分钟)
   └─ Key: "analysis:hash:{contentHash}"
   └─ Value: ContentAnalysisResult
```

---

## 📝 实现步骤

### Day 1: 核心功能 (8小时)

#### 步骤1: 设计DTO类 (1小时)

创建Function的输入/输出DTO:

```java
// AuditRuleFunction.java (60行)
@Data
public class AuditRuleFunction {
    @FunctionProperty(description = "获取所有审核规则")
    public static class Input {
        @JsonProperty(description = "规则类型过滤: SENSITIVE_WORDS,CONTENT_PATTERN,ENGAGEMENT_ANOMALY")
        private String ruleType;
        
        @JsonProperty(description = "是否返回已禁用规则")
        private boolean includeDisabled = false;
    }
    
    @Data
    public static class Output {
        private List<RuleDetail> rules;
        private Integer totalCount;
        private Long cacheRefreshTime;
        
        @Data
        public static class RuleDetail {
            private Long ruleId;
            private String ruleType;
            private String content;
            private String severity;
            private String action;
        }
    }
}

// SensitiveWordFunction.java (50行)
@Data
public class SensitiveWordFunction {
    @FunctionProperty(description = "检查内容中的敏感词")
    public static class Input {
        @JsonProperty(description = "待检查的文本内容")
        private String content;
        
        @JsonProperty(description = "最多返回几个匹配结果")
        private Integer maxResults = 10;
    }
    
    @Data
    public static class Output {
        private List<SensitiveWordMatch> matches;
        private Integer totalMatches;
        private Double riskScore;
        
        @Data
        public static class SensitiveWordMatch {
            private String word;
            private String category;
            private Integer position;
            private String context;
        }
    }
}

// ContentAnalysisFunction.java (40行)
@Data
public class ContentAnalysisFunction {
    @FunctionProperty(description = "深度分析内容风险")
    public static class Input {
        @JsonProperty(description = "待分析的内容")
        private String content;
        
        @JsonProperty(description = "分析维度: KEYWORDS,SENTIMENT,ENGAGEMENT,IMAGES")
        private List<String> dimensions;
    }
    
    @Data
    public static class Output {
        private List<String> keywords;
        private Double sentimentScore;
        private EngagementRisk engagementRisk;
        private List<ImageRiskAnalysis> imageAnalysis;
        private String overallRiskLevel;
    }
}
```

**文件**: 
- src/main/java/com/xhs/audit/ai/function/dto/AuditRuleFunction.java
- src/main/java/com/xhs/audit/ai/function/dto/SensitiveWordFunction.java
- src/main/java/com/xhs/audit/ai/function/dto/ContentAnalysisFunction.java

#### 步骤2: 实现AuditRuleFunctions类 (2小时)

```java
// AuditRuleFunctions.java (180行)
@Component
public class AuditRuleFunctions {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditRuleFunctions.class);
    private static final String RULES_CACHE_KEY = "rules:all";
    private static final String SENSITIVE_WORDS_CACHE_KEY = "sensitive:words";
    
    @Autowired
    private AuditRuleRepository ruleRepository;
    
    @Autowired
    private SensitiveWordRepository sensitiveWordRepository;
    
    @Autowired
    private Cache rulesCache;
    
    @Autowired
    private Cache sensitiveWordsCache;
    
    /**
     * 获取所有审核规则 (LLM Function Calling)
     */
    @Bean
    public Supplier<AuditRuleFunction.Output> getAuditRules() {
        return () -> {
            try {
                // 1. 尝试从Caffeine缓存获取
                AuditRuleFunction.Output cached = rulesCache.getIfPresent(RULES_CACHE_KEY);
                if (cached != null) {
                    logger.info("[缓存命中] 规则缓存,共{}条", cached.getTotalCount());
                    return cached;
                }
                
                // 2. 查询数据库
                List<AuditRule> rules = ruleRepository.findByEnabledTrue();
                List<AuditRuleFunction.Output.RuleDetail> details = rules.stream()
                    .map(this::convertToRuleDetail)
                    .collect(Collectors.toList());
                
                // 3. 构建输出
                AuditRuleFunction.Output output = new AuditRuleFunction.Output();
                output.setRules(details);
                output.setTotalCount(rules.size());
                output.setCacheRefreshTime(System.currentTimeMillis());
                
                // 4. 存入缓存 (1小时TTL)
                rulesCache.put(RULES_CACHE_KEY, output);
                logger.info("[规则加载] 共{}条规则", rules.size());
                
                return output;
            } catch (Exception e) {
                logger.error("[规则获取异常]", e);
                return buildEmptyRulesOutput();
            }
        };
    }
    
    /**
     * 检查敏感词 (LLM Function Calling)
     */
    @Bean
    public Supplier<SensitiveWordFunction.Output> checkSensitiveWords() {
        return () -> {
            try {
                // 实现步骤 (见下方详细代码)
                return new SensitiveWordFunction.Output();
            } catch (Exception e) {
                logger.error("[敏感词检查异常]", e);
                return buildEmptySensitiveWordsOutput();
            }
        };
    }
    
    /**
     * 深度内容分析 (LLM Function Calling)
     */
    @Bean
    public Supplier<ContentAnalysisFunction.Output> analyzeContent() {
        return () -> {
            try {
                // 实现步骤 (见下方详细代码)
                return new ContentAnalysisFunction.Output();
            } catch (Exception e) {
                logger.error("[内容分析异常]", e);
                return buildEmptyAnalysisOutput();
            }
        };
    }
    
    // ---- 私有方法 ----
    
    private AuditRuleFunction.Output.RuleDetail convertToRuleDetail(AuditRule rule) {
        AuditRuleFunction.Output.RuleDetail detail = new AuditRuleFunction.Output.RuleDetail();
        detail.setRuleId(rule.getId());
        detail.setRuleType(rule.getRuleType().toString());
        detail.setContent(rule.getPatternOrKeyword());
        detail.setSeverity(rule.getSeverity().toString());
        detail.setAction(rule.getAction());
        return detail;
    }
    
    private AuditRuleFunction.Output buildEmptyRulesOutput() {
        AuditRuleFunction.Output output = new AuditRuleFunction.Output();
        output.setRules(Collections.emptyList());
        output.setTotalCount(0);
        output.setCacheRefreshTime(System.currentTimeMillis());
        return output;
    }
    
    // ... 其他empty output方法
}
```

**文件**: src/main/java/com/xhs/audit/ai/function/AuditRuleFunctions.java

#### 步骤3: Caffeine缓存配置 (1小时)

```java
// CacheConfiguration.java (80行)
@Configuration
public class CacheConfiguration {
    
    @Bean("rulesCache")
    public Cache rulesCache() {
        return Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .recordStats()
            .build();
    }
    
    @Bean("sensitiveWordsCache")
    public Cache sensitiveWordsCache() {
        return Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(2, TimeUnit.HOURS)
            .recordStats()
            .build();
    }
    
    @Bean("analysisCache")
    public Cache analysisCache() {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();
    }
}
```

**文件**: src/main/java/com/xhs/audit/config/CacheConfiguration.java

### Day 2: 测试和文档 (8小时)

#### 步骤4: 单元测试 (3小时)

```java
// AuditRuleFunctionsTest.java (150行)
@SpringBootTest
@ActiveProfiles("test")
public class AuditRuleFunctionsTest {
    
    @Autowired
    private AuditRuleFunctions functions;
    
    @MockBean
    private AuditRuleRepository ruleRepository;
    
    @MockBean
    private SensitiveWordRepository sensitiveWordRepository;
    
    @Test
    public void testGetAuditRulesSuccess() {
        // 1. 准备测试数据
        List<AuditRule> mockRules = createMockAuditRules();
        Mockito.when(ruleRepository.findByEnabledTrue()).thenReturn(mockRules);
        
        // 2. 执行
        AuditRuleFunction.Output result = functions.getAuditRules().get();
        
        // 3. 验证
        assertNotNull(result);
        assertEquals(2, result.getRules().size());
        assertEquals("SENSITIVE_WORDS", result.getRules().get(0).getRuleType());
    }
    
    @Test
    public void testCheckSensitiveWordsWithMatch() {
        // 检查敏感词匹配
    }
    
    @Test
    public void testAnalyzeContentMultiDimension() {
        // 多维度内容分析
    }
    
    @Test
    public void testCacheHitRate() {
        // 缓存命中率测试
    }
    
    private List<AuditRule> createMockAuditRules() {
        // 构造mock数据
        return new ArrayList<>();
    }
}
```

**文件**: src/test/java/com/xhs/audit/ai/function/AuditRuleFunctionsTest.java

#### 步骤5: 集成测试 (1小时)

```java
// AuditRuleFunctionsIntegrationTest.java (80行)
@SpringBootTest
@Transactional
public class AuditRuleFunctionsIntegrationTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private AuditRuleFunctions functions;
    
    @Test
    public void testEndToEndRuleRetrieval() {
        // 创建真实数据库记录
        // 调用function
        // 验证结果
    }
}
```

#### 步骤6: 性能测试 (1小时)

```java
// AuditRuleFunctionsPerformanceTest.java (60行)
public class AuditRuleFunctionsPerformanceTest {
    
    @Test
    public void testCachePerformance() {
        // 测试缓存提升效率
        // 首次调用: 500ms
        // 缓存命中: 1ms
    }
    
    @Test
    public void testSensitiveWordMatchingPerformance() {
        // 1000个敏感词库,1MB文本
        // 预期 <100ms
    }
}
```

#### 步骤7: 文档编写 (2小时)

**PHASE_2_3_IMPLEMENTATION.md** (200行)
- Function定义说明
- 缓存策略详解
- 性能指标
- 故障排查指南

---

## 🔗 依赖关系

### 需要的实体类

**AuditRule** (已存在)
```java
@Entity
public class AuditRule {
    private Long id;
    private RuleType ruleType;        // SENSITIVE_WORDS, CONTENT_PATTERN, etc
    private String patternOrKeyword;  // 规则内容
    private Severity severity;         // HIGH, MEDIUM, LOW
    private String action;             // REJECT, FLAG, REVIEW
    private Boolean enabled;
}
```

**SensitiveWord** (已存在)
```java
@Entity
public class SensitiveWord {
    private Long id;
    private String word;
    private WordCategory category;
    private Integer level;            // 敏感度等级 1-5
}
```

### 需要的Repository

```java
// AuditRuleRepository需要添加
Optional<List<AuditRule>> findByEnabledTrue();
Optional<List<AuditRule>> findByRuleTypeAndEnabledTrue(RuleType type);

// SensitiveWordRepository需要添加
Optional<List<SensitiveWord>> findByCategory(WordCategory category);
Optional<SensitiveWord> findByWord(String word);
```

---

## 📊 预期成果

### 代码指标

| 指标 | 目标值 |
|------|-------|
| AuditRuleFunctions代码行数 | 180-200 |
| DTO类代码行数 | 150-180 |
| 缓存配置代码行数 | 80-100 |
| 单元测试代码行数 | 150-200 |
| 总代码行数 | 560-680 |

### 质量指标

| 指标 | 目标值 |
|------|-------|
| 测试通过率 | 100% |
| 代码覆盖率 | ≥85% |
| 异常处理 | 100% |
| 日志记录 | 所有关键路径 |

### 性能指标

| 指标 | 目标值 |
|------|-------|
| 首次规则加载 | <500ms |
| 缓存命中响应 | <5ms |
| 敏感词检查 (100词) | <50ms |
| 内容分析 (1MB) | <200ms |

---

## 🚀 成功标准

### 功能完成

- [ ] AuditRuleFunctions类完整实现
- [ ] 所有三个Function Beans正确定义
- [ ] Caffeine缓存正确配置
- [ ] Repository方法正确添加

### 测试完成

- [ ] 所有单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试达到目标
- [ ] 代码覆盖率≥85%

### 文档完成

- [ ] 实现文档完成
- [ ] API文档完成
- [ ] 故障排查指南完成
- [ ] 代码注释完整

### 部署就绪

- [ ] 代码编译成功
- [ ] 无编译警告
- [ ] 无运行时异常
- [ ] 与CrawlerService集成成功

---

## 📅 时间安排

```
Day 1 (2026-02-03)
├─ 08:00-09:00: DTO类设计和编写
├─ 09:00-11:00: AuditRuleFunctions核心实现
├─ 11:00-12:00: Caffeine缓存配置
└─ 14:00-17:00: 编写单元测试框架

Day 2 (2026-02-04)
├─ 08:00-10:00: 完成单元测试
├─ 10:00-11:00: 集成测试
├─ 11:00-12:00: 性能测试
└─ 14:00-17:00: 文档编写和代码review
```

---

## 📋 检查清单

在开始实现前检查:

- [ ] PlaywrightManager运行正常
- [ ] CrawlerService编译成功
- [ ] AuditRule/SensitiveWord实体存在
- [ ] 数据库已迁移
- [ ] 测试框架配置完成
- [ ] Caffeine依赖已添加到pom.xml

在提交前检查:

- [ ] `mvn clean compile` 成功
- [ ] `mvn test` 全部通过
- [ ] Javadoc完整
- [ ] 无TODO注释
- [ ] Git commit消息明确

---

**文档创建时间**: 2026-01-27  
**预计完成时间**: 2026-02-04  
**下一阶段**: Phase 2.4 (ContentAuditAgent)
