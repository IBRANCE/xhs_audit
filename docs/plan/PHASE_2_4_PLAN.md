# Phase 2.4: ContentAuditAgent - 详细规划

## 📋 阶段概述

**目标**: 实现LLM审核智能体,集成ChatClient+Function Calling完成内容审核

**工作量**: 2.5天  
**完成日期**: 2026-02-07  
**关键交付**: ContentAuditAgent.java + AuditDecision DTO + 完整测试

**前置条件**: Phase 2.2 (CrawlerService) ✓ 和 Phase 2.3 (AuditRuleFunctions) ✓

---

## 🏗️ 架构设计

### 智能体工作流

```
用户输入 (URL)
    ↓
CrawlerService (爬虫)
    ├─ 返回: XhsContent (标题/描述/图片/标签/作者/时间)
    └─ 缓存: Redis + PostgreSQL
    ↓
ContentAuditAgent (审核智能体)
    ├─ 步骤1: 准备审核Context
    │   ├─ System Prompt: 审核规则和注意事项
    │   ├─ User Message: 爬虫得到的内容
    │   └─ Function Definitions: AuditRuleFunctions
    │
    ├─ 步骤2: 调用ChatClient (o1-mini模型)
    │   ├─ 模型分析内容
    │   ├─ 调用getAuditRules() Function
    │   ├─ 调用checkSensitiveWords() Function
    │   ├─ 返回结构化输出
    │   └─ 提取Decision
    │
    ├─ 步骤3: 结构化处理
    │   ├─ StructuredOutputConverter解析JSON
    │   ├─ AuditDecision DTO验证
    │   └─ 置信度计算
    │
    └─ 步骤4: 结果存储
        ├─ 保存AuditResult到PostgreSQL
        ├─ 更新XhsContent审核状态
        └─ 发送通知 (可选)
    ↓
返回: AuditDecision
    ├─ 决策: APPROVED / REJECTED / FLAGGED
    ├─ 原因: 具体违规类别
    ├─ 置信度: 0-1
    └─ 建议: 处理建议
```

### 核心组件

```
┌─ ContentAuditAgent (200行)
│  ├─ ChatClient配置
│  ├─ auditContent(XhsContent) → AuditDecision
│  ├─ buildSystemPrompt() → String
│  ├─ buildUserMessage(XhsContent) → UserMessage
│  └─ extractAuditDecision(ChatResponse) → AuditDecision
│
├─ AuditDecisionDTO (80行)
│  ├─ decision: String (APPROVED/REJECTED/FLAGGED)
│  ├─ reasons: List<String> (具体原因)
│  ├─ confidence: Double (0-1)
│  ├─ riskLevel: String (LOW/MEDIUM/HIGH)
│  └─ suggestions: List<String> (处理建议)
│
├─ AuditPromptBuilder (100行)
│  ├─ buildSystemPrompt() → 600+ tokens
│  ├─ buildUserContent() → 灵活格式
│  └─ buildFunctionContext() → 规则说明
│
└─ 支持类 (120行)
   ├─ AuditException
   ├─ AuditContext
   └─ DecisionValidator
```

---

## 📝 实现步骤

### Day 1: 核心实现 (8小时)

#### 步骤1: System Prompt设计 (1小时)

创建系统级提示词,告诉模型它的角色和任务:

```java
// AuditPromptBuilder.java (120行)
@Component
public class AuditPromptBuilder {
    
    private static final String SYSTEM_PROMPT_TEMPLATE = """
        你是一个专业的内容审核员,负责审核小红书(XHS)平台上的用户生成内容。
        
        【审核目标】
        根据平台规则,判断内容是否包含以下违规因素:
        1. 敏感词汇 (政治、暴力、色情、违禁品)
        2. 虚假宣传 (医疗、投资、减肥等)
        3. 骗局和诈骗内容
        4. 刷屏和垃圾信息
        5. 知识产权侵犯
        
        【审核维度】
        - 标题和描述: 重点检查标题中的不当言论
        - 图片: 检查是否包含不当内容提示
        - 标签: 检查标签是否被滥用
        - 互动数据: 异常的点赞/评论可能表示刷屏
        - 发布者信息: 检查是否存在虚假认证
        
        【决策标准】
        - APPROVED: 内容完全合规,无任何风险
        - FLAGGED: 内容存在中等风险,需要人工审核
        - REJECTED: 内容明确违规,应当删除
        
        【重要提示】
        1. 避免过度敏感 (不要因为政治术语就拒绝)
        2. 考虑上下文 (教育内容≠宣传)
        3. 量化理由 (不要说"感觉违规")
        4. 提供建议 (即使拒绝也要说如何修改)
        
        请使用以下格式返回决策:
        {
          "decision": "APPROVED|FLAGGED|REJECTED",
          "reasons": ["具体原因1", "具体原因2"],
          "confidence": 0.95,
          "riskLevel": "LOW|MEDIUM|HIGH",
          "suggestions": ["建议1", "建议2"]
        }
        """;
    
    public String buildSystemPrompt() {
        return SYSTEM_PROMPT_TEMPLATE;
    }
    
    public String buildUserMessage(XhsContent content) {
        return String.format("""
            请审核以下小红书内容:
            
            【基本信息】
            - 发布者: %s
            - 发布时间: %s
            - 点赞: %d | 评论: %d | 收藏: %d
            
            【内容】
            标题: %s
            描述: %s
            
            【图片】
            共%d张图片,URL: %s
            
            【标签】
            %s
            
            【相似内容检查】
            - 是否为热门话题下的重复内容?
            - 发布频率是否异常高?
            
            请进行全面审核。
            """,
            content.getAuthorName(),
            content.getPublishTime(),
            content.getLikes(),
            content.getComments(),
            content.getFavorites(),
            content.getTitle(),
            content.getDescription(),
            content.getImageUrls().size(),
            String.join(", ", content.getImageUrls().stream().limit(3).toList()),
            String.join(", ", content.getTags())
        );
    }
}
```

#### 步骤2: DTO类设计 (1小时)

```java
// AuditDecision.java (80行)
@Data
@Builder
@JsonDeserialize
public class AuditDecision {
    
    @JsonProperty("decision")
    @NotNull(message = "审核决策不能为空")
    private Decision decision;        // APPROVED, REJECTED, FLAGGED
    
    @JsonProperty("reasons")
    private List<String> reasons;    // 具体违规原因
    
    @JsonProperty("confidence")
    @Range(min = 0, max = 1, message = "置信度应在0-1之间")
    private Double confidence;       // 0-1置信度
    
    @JsonProperty("riskLevel")
    private RiskLevel riskLevel;     // LOW, MEDIUM, HIGH
    
    @JsonProperty("suggestions")
    private List<String> suggestions;// 处理建议
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata; // 其他信息
    
    // 枚举定义
    public enum Decision {
        APPROVED,    // 通过
        REJECTED,    // 拒绝
        FLAGGED      // 标记待审
    }
    
    public enum RiskLevel {
        LOW,         // 低风险
        MEDIUM,      // 中风险
        HIGH         // 高风险
    }
    
    // 验证方法
    public boolean isValid() {
        if (decision == null) return false;
        if (confidence == null || confidence < 0 || confidence > 1) return false;
        if (riskLevel == null) return false;
        return true;
    }
    
    // 风险评分
    public Double calculateRiskScore() {
        return riskLevel == RiskLevel.HIGH ? 0.8 :
               riskLevel == RiskLevel.MEDIUM ? 0.5 : 0.2;
    }
}

// AuditContext.java (60行) - 审核上下文
@Data
@Builder
public class AuditContext {
    private String contentUrl;
    private XhsContent content;
    private Long userId;
    private LocalDateTime auditTime;
    private String auditReason;      // 触发审核的原因
    private String auditRuleVersion; // 规则版本
    private Long executionTimeMs;    // 审核耗时
    private Map<String, Object> metadata;
}
```

#### 步骤3: ChatClient配置 (1小时)

```java
// AuditAgentConfiguration.java (140行)
@Configuration
public class AuditAgentConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditAgentConfiguration.class);
    
    @Autowired
    private ChatClient.Builder chatClientBuilder;
    
    @Autowired
    private AuditRuleFunctions auditRuleFunctions;
    
    /**
     * 配置审核专用ChatClient
     */
    @Bean("auditChatClient")
    public ChatClient auditChatClient() {
        return chatClientBuilder
            .defaultOptions(OpenAiChatOptions.builder()
                .withModel("gpt-4-turbo")           // 使用更强的模型
                .withTemperature(0.3)                // 降低温度,保证一致性
                .withTopP(0.8)
                .withMaxTokens(500)                  // 限制输出长度
                .withLogitBias(Collections.emptyMap())
                .build())
            .build();
    }
    
    /**
     * 注册Function Calling工具
     */
    @Bean
    public FunctionCallbackRegistration auditRulesFunction() {
        return FunctionCallbackRegistration.builder()
            .functionName("getAuditRules")
            .description("获取平台审核规则")
            .function(auditRuleFunctions.getAuditRules())
            .build();
    }
    
    @Bean
    public FunctionCallbackRegistration sensitiveWordsFunction() {
        return FunctionCallbackRegistration.builder()
            .functionName("checkSensitiveWords")
            .description("检查内容中的敏感词")
            .function(auditRuleFunctions.checkSensitiveWords())
            .build();
    }
}
```

#### 步骤4: 核心Agent实现 (2小时)

```java
// ContentAuditAgent.java (200行)
@Service
@Slf4j
public class ContentAuditAgent {
    
    @Autowired
    @Qualifier("auditChatClient")
    private ChatClient chatClient;
    
    @Autowired
    private AuditPromptBuilder promptBuilder;
    
    @Autowired
    private AuditResultRepository auditResultRepository;
    
    @Autowired
    private XhsContentRepository contentRepository;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 审核XHS内容 (主入口)
     */
    public AuditDecision auditContent(XhsContent content) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 构建审核消息
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userMessage = promptBuilder.buildUserMessage(content);
            
            log.info("[审核开始] 内容ID: {}, URL: {}", content.getId(), content.getUrl());
            
            // 2. 调用LLM
            ChatResponse response = chatClient.call(
                new Prompt(
                    Arrays.asList(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userMessage)
                    ),
                    OpenAiChatOptions.builder()
                        .withFunction("getAuditRules")
                        .withFunction("checkSensitiveWords")
                        .build()
                )
            );
            
            // 3. 解析决策
            AuditDecision decision = parseAuditResponse(response);
            decision.setConfidence(decision.getConfidence() != null ? 
                decision.getConfidence() : 0.85);
            
            // 4. 保存结果
            long executionTime = System.currentTimeMillis() - startTime;
            saveAuditResult(content, decision, executionTime);
            
            log.info("[审核完成] 决策: {}, 耗时: {}ms", 
                decision.getDecision(), executionTime);
            
            return decision;
            
        } catch (Exception e) {
            log.error("[审核异常] 内容ID: {}", content.getId(), e);
            return buildErrorDecision(e);
        }
    }
    
    /**
     * 批量审核内容
     */
    public List<AuditDecision> auditContentBatch(List<XhsContent> contents) {
        return contents.stream()
            .parallel()
            .map(this::auditContent)
            .collect(Collectors.toList());
    }
    
    /**
     * 解析LLM响应
     */
    private AuditDecision parseAuditResponse(ChatResponse response) {
        try {
            String content = response.getResult().getOutput().getContent();
            
            // 提取JSON格式的决策
            String jsonStr = extractJson(content);
            AuditDecision decision = objectMapper.readValue(
                jsonStr, 
                AuditDecision.class
            );
            
            // 验证决策有效性
            if (!decision.isValid()) {
                throw new IllegalArgumentException("无效的审核决策: " + decision);
            }
            
            return decision;
            
        } catch (JsonProcessingException e) {
            log.error("[决策解析失败]", e);
            throw new AuditException("Failed to parse audit decision", e);
        }
    }
    
    /**
     * 从文本中提取JSON
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start == -1 || end == -1) {
            throw new AuditException("Response does not contain valid JSON: " + text);
        }
        
        return text.substring(start, end + 1);
    }
    
    /**
     * 保存审核结果
     */
    private void saveAuditResult(XhsContent content, AuditDecision decision, long executionTime) {
        AuditResult result = AuditResult.builder()
            .contentId(content.getId())
            .contentUrl(content.getUrl())
            .decision(decision.getDecision().toString())
            .reasons(String.join("|", decision.getReasons()))
            .confidence(decision.getConfidence())
            .riskLevel(decision.getRiskLevel().toString())
            .suggestions(String.join("|", decision.getSuggestions()))
            .auditAgent("ContentAuditAgent")
            .executionTimeMs(executionTime)
            .auditTime(LocalDateTime.now())
            .build();
        
        auditResultRepository.save(result);
        
        // 更新内容审核状态
        content.setAuditStatus(decision.getDecision().toString());
        content.setAuditedAt(LocalDateTime.now());
        contentRepository.save(content);
    }
    
    /**
     * 构建错误决策
     */
    private AuditDecision buildErrorDecision(Exception e) {
        return AuditDecision.builder()
            .decision(AuditDecision.Decision.FLAGGED)
            .reasons(Arrays.asList("审核异常: " + e.getMessage()))
            .confidence(0.0)
            .riskLevel(AuditDecision.RiskLevel.MEDIUM)
            .suggestions(Arrays.asList("请进行人工审核"))
            .build();
    }
}
```

#### 步骤5: 异常处理 (1小时)

```java
// AuditException.java (40行)
public class AuditException extends RuntimeException {
    
    private final String errorCode;
    private final Map<String, Object> context;
    
    public AuditException(String message) {
        super(message);
        this.errorCode = "AUDIT_ERROR";
        this.context = new HashMap<>();
    }
    
    public AuditException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUDIT_ERROR";
        this.context = new HashMap<>();
    }
    
    public AuditException(String errorCode, String message, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }
}
```

### Day 2: 测试和集成 (8小时)

#### 步骤6: 单元测试 (3小时)

```java
// ContentAuditAgentTest.java (180行)
@SpringBootTest
@ActiveProfiles("test")
public class ContentAuditAgentTest {
    
    @Autowired
    private ContentAuditAgent agent;
    
    @MockBean
    private ChatClient chatClient;
    
    @MockBean
    private AuditResultRepository auditResultRepository;
    
    @Test
    public void testAuditContentApproved() {
        // 准备mock数据
        XhsContent content = createMockXhsContent();
        
        // Mock LLM响应
        String llmResponse = """
            {"decision": "APPROVED", "reasons": [], "confidence": 0.98, 
             "riskLevel": "LOW", "suggestions": []}
            """;
        
        // 执行审核
        AuditDecision decision = agent.auditContent(content);
        
        // 验证
        assertEquals(AuditDecision.Decision.APPROVED, decision.getDecision());
        assertEquals(0.98, decision.getConfidence(), 0.01);
    }
    
    @Test
    public void testAuditContentRejected() {
        XhsContent content = createMockXhsContent();
        // 包含违禁词
        content.setTitle("免费获得处方药");
        
        // ... 执行和验证
    }
    
    @Test
    public void testAuditContentFlagged() {
        // 中等风险内容
    }
    
    @Test
    public void testBatchAudit() {
        List<XhsContent> contents = createMockXhsContentList(10);
        List<AuditDecision> decisions = agent.auditContentBatch(contents);
        assertEquals(10, decisions.size());
    }
    
    private XhsContent createMockXhsContent() {
        return XhsContent.builder()
            .url("https://xhs.com/xxx")
            .title("Test Content")
            .description("Test Description")
            .build();
    }
}
```

#### 步骤7: 集成测试 (2小时)

```java
// ContentAuditAgentIntegrationTest.java (120行)
@SpringBootTest
@Transactional
public class ContentAuditAgentIntegrationTest {
    
    @Autowired
    private CrawlerService crawlerService;
    
    @Autowired
    private ContentAuditAgent auditAgent;
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    public void testEndToEndAuditPipeline() throws Exception {
        // 1. 爬取内容
        String url = "https://xhs.com/test";
        XhsContent content = crawlerService.crawlContent(url);
        
        // 2. 审核内容
        AuditDecision decision = auditAgent.auditContent(content);
        
        // 3. 验证结果
        assertNotNull(decision);
        assertTrue(decision.isValid());
        
        // 4. 验证数据库状态
        XhsContent updated = entityManager.find(XhsContent.class, content.getId());
        assertNotNull(updated.getAuditedAt());
    }
}
```

#### 步骤8: 性能测试 (1.5小时)

```java
// ContentAuditAgentPerformanceTest.java (100行)
@SpringBootTest
public class ContentAuditAgentPerformanceTest {
    
    @Autowired
    private ContentAuditAgent agent;
    
    @Test
    public void testAuditLatency() {
        XhsContent content = createMockXhsContent();
        
        long startTime = System.currentTimeMillis();
        AuditDecision decision = agent.auditContent(content);
        long duration = System.currentTimeMillis() - startTime;
        
        // 预期<2000ms
        assertTrue(duration < 2000, "审核耗时超过2秒: " + duration + "ms");
    }
    
    @Test
    public void testBatchAuditThroughput() {
        List<XhsContent> contents = createMockXhsContentList(100);
        
        long startTime = System.currentTimeMillis();
        agent.auditContentBatch(contents);
        long duration = System.currentTimeMillis() - startTime;
        
        double throughput = 100.0 / (duration / 1000.0);
        System.out.println("吞吐量: " + throughput + " 内容/秒");
        
        // 预期>30 内容/秒
        assertTrue(throughput > 30);
    }
}
```

#### 步骤9: 文档和示例 (1.5小时)

**PHASE_2_4_IMPLEMENTATION.md** (250行)
- LLM集成指南
- Function Calling工作流
- 性能优化建议
- 常见问题排查

**使用示例**:
```java
// 基本用法
@RestController
public class AuditController {
    
    @Autowired
    private CrawlerService crawler;
    
    @Autowired
    private ContentAuditAgent auditAgent;
    
    @PostMapping("/api/audit")
    public AuditDecision auditUrl(@RequestParam String url) {
        // 1. 爬取内容
        XhsContent content = crawler.crawlContent(url);
        
        // 2. 审核内容
        return auditAgent.auditContent(content);
    }
}
```

---

## 🔗 依赖关系

### 必须完成

- [x] Phase 2.2: CrawlerService (获取内容)
- [x] Phase 2.3: AuditRuleFunctions (Function定义)
- [ ] 可选: Phase 2.5 (敏感词库)

### 需要的实体

**XhsContent** (已存在,需要扩展)
```java
@Entity
public class XhsContent {
    private String auditStatus;      // 审核状态
    private LocalDateTime auditedAt; // 审核时间
}
```

**AuditResult** (新建)
```java
@Entity
public class AuditResult {
    private Long id;
    private Long contentId;
    private String decision;         // APPROVED/REJECTED/FLAGGED
    private String reasons;
    private Double confidence;
    private String riskLevel;
    private LocalDateTime auditTime;
    private Long executionTimeMs;    // 执行耗时
}
```

---

## 📊 预期成果

### 代码指标

| 组件 | 行数 | 说明 |
|------|------|------|
| AuditPromptBuilder | 120 | 提示词构建 |
| ContentAuditAgent | 200 | 核心智能体 |
| AuditDecision DTO | 80 | 决策数据类 |
| AuditAgentConfiguration | 140 | Spring配置 |
| 单元测试 | 180 | 核心测试 |
| 集成测试 | 120 | E2E测试 |
| **总计** | **840** | **完整实现** |

### 质量指标

| 指标 | 目标 |
|------|------|
| 测试通过率 | 100% |
| 代码覆盖率 | ≥85% |
| 异常处理 | 100% |
| 日志记录 | 所有关键路径 |

### 性能指标

| 指标 | 目标 | 说明 |
|------|------|------|
| 单条审核延迟 | <2s | 包括LLM API调用 |
| 批量吞吐量 | >30条/秒 | 并行处理 |
| LLM API超时 | <1500ms | 网络耗时 |
| 解析耗时 | <50ms | JSON处理 |

---

## 🚀 成功标准

### 功能完成

- [ ] ContentAuditAgent完整实现
- [ ] AuditDecision DTO正确定义
- [ ] ChatClient正确配置
- [ ] Function Calling正确集成
- [ ] 与CrawlerService完整集成

### 测试完成

- [ ] 所有单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试达到目标
- [ ] 代码覆盖率≥85%

### 文档完成

- [ ] 实现文档完成
- [ ] LLM集成指南完成
- [ ] 使用示例完成
- [ ] API文档完成

### 部署就绪

- [ ] 代码编译成功
- [ ] 与Phase 2.2/2.3无冲突
- [ ] 无运行时异常
- [ ] 生成正确的审核决策

---

## 📅 时间安排

```
Day 1 (2026-02-05)
├─ 08:00-09:00: Prompt设计
├─ 09:00-10:00: DTO设计
├─ 10:00-11:30: ChatClient配置
└─ 14:00-17:00: 核心Agent实现

Day 2 (2026-02-06)
├─ 08:00-09:00: 异常处理
├─ 09:00-12:00: 完成单元测试
├─ 14:00-15:30: 集成测试
└─ 15:30-17:00: 性能测试

Day 3 (2026-02-07)
├─ 08:00-10:00: 文档编写
├─ 10:00-11:30: 示例代码
└─ 14:00-17:00: 最终测试和提交
```

---

## 📋 检查清单

在开始实现前检查:

- [ ] PlaywrightManager和CrawlerService运行正常
- [ ] AuditRuleFunctions编译成功
- [ ] Spring AI库已添加到pom.xml
- [ ] ChatClient Bean可以注入
- [ ] OpenAI API密钥已配置
- [ ] 数据库已迁移 (AuditResult表)

在提交前检查:

- [ ] `mvn clean compile` 成功
- [ ] `mvn test` 全部通过
- [ ] Javadoc完整
- [ ] 无TODO注释
- [ ] 与Phase 2.2/2.3集成测试通过

---

**文档创建时间**: 2026-01-27  
**预计完成时间**: 2026-02-07  
**下一阶段**: Phase 2.5 (敏感词库优化) + Phase 3 (API层)
