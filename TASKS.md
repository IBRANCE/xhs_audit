# 开发任务跟踪表 (Task Tracker)

**项目**：小红书内容审核Agent系统  
**范围**：Phase 2.2 - Phase 4  
**更新时间**：2026年1月27日

---

## 📊 总体进度

```
[████████░░░░░░░░░░░] 40% - Phase 1 & 2.1 完成
[░░░░░░░░░░░░░░░░░░░]  0% - Phase 2.2-2.5 进行中
```

- **已完成**: 5/20 任务 (25%)
- **进行中**: 0/15 任务 (0%)
- **待开始**: 15/15 任务 (0%)

---

## 🎯 Phase 2.2: CrawlerService爬虫服务

**计划工作量**: 5天  
**预计完成**: 2026年2月3日  
**优先级**: 🔴 **高** (后续所有功能都依赖)

### 任务分解

| # | 任务 | 描述 | 难度 | 天数 | 状态 | 负责人 |
|---|------|------|------|------|------|--------|
| 2.2.1 | CrawlerService框架 | 实现爬虫服务主体类，注入PlaywrightManager | ⭐⭐ | 1 | ⬜ | - |
| 2.2.2 | getPostContent() | 爬取帖子内容（标题、正文、图片URL、Tag） | ⭐⭐⭐ | 1 | ⬜ | - |
| 2.2.3 | getPostMetadata() | 提取元数据（发布者、时间、点赞数等） | ⭐⭐ | 0.5 | ⬜ | - |
| 2.2.4 | 缓存集成 | Redis缓存检查、PostgreSQL查询、缓存回填 | ⭐⭐ | 1 | ⬜ | - |
| 2.2.5 | 数据库存储 | JSONB字段处理、事务管理、性能优化 | ⭐⭐ | 1 | ⬜ | - |
| 2.2.6 | CrawlerServiceTest | 12个测试用例，覆盖各种场景 | ⭐⭐ | 1 | ⬜ | - |

### 依赖关系
```
2.2.1 → 2.2.2, 2.2.3
2.2.2, 2.2.3 → 2.2.4
2.2.4 → 2.2.5
所有 → 2.2.6
```

### 关键代码片段

```java
// CrawlerService应该实现的接口
@Service
public class CrawlerService {
    @Autowired private PlaywrightManager playwrightManager;
    @Autowired private XhsContentRepository contentRepository;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    
    // 1. 检查缓存
    public XhsContent crawlContent(String url) {
        // 1.1 Redis检查
        // 1.2 如果命中，返回
        // 1.3 PostgreSQL检查
        // 1.4 如果命中，更新Redis，返回
        // 1.5 执行爬虫
        // 1.6 保存到Redis和PostgreSQL
    }
    
    // 2. 爬虫核心逻辑
    private XhsContent executeWebScraping(String url) {
        try (PageWrapper page = playwrightManager.borrowPage()) {
            page.page.navigate(url);
            // 提取内容...
        }
    }
}
```

---

## 🎯 Phase 2.3: AuditRuleFunctions Function Calling工具

**计划工作量**: 2天  
**预计完成**: 2026年2月5日  
**优先级**: 🟠 **中高** (与2.4并行)

### 任务分解

| # | 任务 | 描述 | 难度 | 天数 | 状态 | 负责人 |
|---|------|------|------|------|------|--------|
| 2.3.1 | Function定义 | 定义LLM可调用的审核工具函数 | ⭐⭐ | 1 | ⬜ | - |
| 2.3.2 | 规则缓存 | Caffeine缓存规则列表和敏感词库 | ⭐⭐ | 0.5 | ⬜ | - |
| 2.3.3 | AuditRuleFunctionsTest | 功能测试和性能基准 | ⭐⭐ | 0.5 | ⬜ | - |

### 核心方法

```java
@Component
public class AuditRuleFunctions {
    // 方法1: 获取审核规则
    public List<AuditRule> getAuditRules()
    
    // 方法2: 敏感词检查
    public List<String> checkSensitiveWords(String content)
    
    // 方法3: 内容深度分析
    public ContentAnalysis analyzeContent(String content)
}
```

---

## 🎯 Phase 2.4: ContentAuditAgent LLM审核引擎

**计划工作量**: 2天  
**预计完成**: 2026年2月5日  
**优先级**: 🟠 **中高** (与2.3并行)

### 任务分解

| # | 任务 | 描述 | 难度 | 天数 | 状态 | 负责人 |
|---|------|------|------|------|------|--------|
| 2.4.1 | Agent框架 | ChatClient配置、System Prompt设计 | ⭐⭐⭐ | 1 | ⬜ | - |
| 2.4.2 | 输出格式 | StructuredOutputConverter确保JSON格式 | ⭐⭐ | 0.5 | ⬜ | - |
| 2.4.3 | ContentAuditAgentTest | 完整的审核流程测试 | ⭐⭐ | 0.5 | ⬜ | - |

### DTO设计

```java
@Data
public class AuditDecision {
    private boolean isViolation;              // 是否违规
    private List<String> violations;           // 违规类别
    private String riskLevel;                  // HIGH/MEDIUM/LOW
    private double confidence;                 // 0-1置信度
    private String explanation;                // 详细说明
    private LocalDateTime auditTime;           // 审核时间
}
```

---

## 🎯 Phase 2.5: 敏感词优化

**计划工作量**: 2天  
**预计完成**: 2026年2月10日  
**优先级**: 🟡 **中** (可在Phase 3进行)

### 任务分解

| # | 任务 | 描述 | 难度 | 天数 | 状态 | 负责人 |
|---|------|------|------|------|------|--------|
| 2.5.1 | 方案选择 | 评估AC自动机 vs sensitive-word-filter | ⭐ | 0.5 | ⬜ | - |
| 2.5.2 | Matcher实现 | 敏感词匹配器实现和缓存 | ⭐⭐ | 1 | ⬜ | - |
| 2.5.3 | 性能测试 | 基准测试验证1000字 < 1ms目标 | ⭐⭐ | 0.5 | ⬜ | - |

### 性能目标
- **目标**: 1000字文本匹配5000词库 < 1ms
- **当前**: ~50ms (未优化)
- **改进**: AC自动机 或 sensitive-word-filter库

---

## 🎯 Phase 3: REST API和业务集成

**计划工作量**: 5天  
**预计完成**: 2026年2月17日  
**优先级**: 🟡 **中** (Phase 2.5并行进行)

### 任务分解

| # | 任务 | 描述 | 难度 | 天数 | 状态 | 负责人 |
|---|------|------|------|------|------|--------|
| 3.1 | AuditController | REST API端点实现 | ⭐⭐⭐ | 1.5 | ⬜ | - |
| 3.2 | ContentAuditService | 业务流程编排 | ⭐⭐⭐ | 1.5 | ⬜ | - |
| 3.3 | ExcelImportService | Excel解析和批量导入 | ⭐⭐⭐ | 1 | ⬜ | - |
| 3.4 | AuditJobService | 异步任务管理 | ⭐⭐ | 1 | ⬜ | - |
| 3.5 | 集成测试 | API端到端测试 | ⭐⭐ | 1 | ⬜ | - |

### API设计

```
POST   /api/audit/content              - 单条审核
POST   /api/audit/upload               - 文件上传
GET    /api/audit/job/{jobId}          - 查询进度
GET    /api/audit/results              - 查询结果
GET    /api/audit/rules                - 获取规则
GET    /api/health                     - 健康检查
```

---

## 🎯 Phase 4: 测试和优化

**计划工作量**: 5天  
**预计完成**: 2026年2月28日  
**优先级**: 🟢 **低** (最后进行)

### 任务分解

| # | 任务 | 描述 | 难度 | 天数 | 状态 | 负责人 |
|---|------|------|------|------|------|--------|
| 4.1 | 单元测试 | 补充覆盖率至80%+ | ⭐⭐ | 2 | ⬜ | - |
| 4.2 | 集成测试 | E2E完整流程测试 | ⭐⭐⭐ | 1.5 | ⬜ | - |
| 4.3 | 性能优化 | JVM调优、查询优化、缓存调参 | ⭐⭐⭐ | 1 | ⬜ | - |
| 4.4 | 文档完善 | API、架构、部署文档 | ⭐ | 0.5 | ⬜ | - |

### 测试覆盖目标

```
单元测试:    >= 80%
集成测试:    100% API端点
性能测试:    所有KPI达成
代码审查:    100%通过
```

---

## 🏁 验收标准

### Phase 2.2验收
- [ ] 代码完成，无TODO
- [ ] 12个测试用例通过
- [ ] 覆盖率 >= 80%
- [ ] 爬虫延迟 < 5s
- [ ] 缓存命中率 > 70%
- [ ] 文档完整

### Phase 2.3-2.4验收
- [ ] 所有Function正常工作
- [ ] LLM调用成功率 > 99%
- [ ] 15个测试用例通过
- [ ] 审核决策格式正确（JSON）
- [ ] 多轮对话支持

### Phase 2.5验收
- [ ] 敏感词匹配 < 1ms
- [ ] 缓存命中率 > 90%
- [ ] 性能基准报告

### Phase 3验收
- [ ] API全部实现
- [ ] 集成测试通过
- [ ] 吞吐量 > 200 req/s
- [ ] Excel处理支持

### Phase 4验收
- [ ] 测试覆盖率 >= 80%
- [ ] 所有KPI达成
- [ ] 文档完整可用
- [ ] 性能基准达成

---

## 📈 风险跟踪

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| LLM费用超支 | 中 | 预算溢出 | 实现速率限制 |
| 爬虫效率低 | 中 | 交付延迟 | 并行优化 |
| 缓存不一致 | 低 | 数据错误 | 严格的TTL |
| 内存溢出 | 低 | 系统崩溃 | 监控 + 自动清理 |

---

## 📝 周报模板

### 第N周总结 (2026年M月D日 - M月D日)

**完成事项**：
- [ ] 任务xxx
- [ ] 任务yyy

**进行中**：
- [ ] 任务aaa
- [ ] 任务bbb

**阻碍因素**：
- (无 / 说明)

**下周计划**：
- [ ] 任务111
- [ ] 任务222

**代码提交**：
- [ ] Git提交已推送
- [ ] 覆盖率有提升

**指标**：
- 代码行数: XXX
- 测试用例: XXX
- 覆盖率: XX%

---

**最后更新**：2026年1月27日  
**下次更新**：2026年2月3日
