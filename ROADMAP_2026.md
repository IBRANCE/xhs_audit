# XHS Audit 项目完整路线图 - 2026版本

## 📊 项目总体进度

```
┌─ Phase 1: 基础设施 ████████░░ 100% ✅ 完成
│  └─ 项目结构、依赖、配置、测试框架
│
├─ Phase 2: 核心功能 (审核引擎) 20% 进行中
│  ├─ Phase 2.1: PlaywrightManager ████████░░ 100% ✅ 完成 (2026-01-26)
│  ├─ Phase 2.2: CrawlerService ████████░░ 100% ✅ 完成 (2026-01-27)
│  ├─ Phase 2.3: AuditRuleFunctions ░░░░░░░░░░ 0% 🚀 计划中 (2026-02-03 - 02-04)
│  ├─ Phase 2.4: ContentAuditAgent ░░░░░░░░░░ 0% 🚀 计划中 (2026-02-05 - 02-07)
│  └─ Phase 2.5: 敏感词库优化 ░░░░░░░░░░ 0% 📋 计划中 (2026-02-10)
│
├─ Phase 3: API层和集成 0% 📋 计划中
│  ├─ AuditController (REST端点)
│  ├─ Excel数据导入
│  ├─ 任务队列和异步处理
│  └─ 数据统计和报表
│
└─ Phase 4: 测试和优化 0% 📋 计划中
   ├─ 端到端测试
   ├─ 性能优化
   ├─ 部署脚本
   └─ 文档完善
```

**总体完成度**: 25% (3/12 大型功能块完成)

---

## 🎯 Phase细节规划

### ✅ Phase 1: 基础设施 (完成)

**交付成果**:
- 完整的Maven项目结构
- Spring Boot 3.5 + Java 17配置
- Docker + PostgreSQL + Redis基础设施
- 8个核心实体 (Entity)
- 完整的测试框架

**关键指标**:
- 编译: BUILD SUCCESS ✓
- 测试: 0个单元测试 (未开始)
- 代码: 1500+行

---

### ✅ Phase 2.1: PlaywrightManager (2026-01-26完成)

**核心功能**:
- Playwright浏览器实例池管理
- 页面生命周期管理 (PageWrapper)
- 异常处理和自动重试
- 资源清理和内存管理

**交付成果**:
- PlaywrightManager.java (445行)
- PlaywrightManagerTest.java (250行)
- 13个单元测试,100%通过率
- BUILD SUCCESS ✓

**关键指标**:
- 代码覆盖率: 85%+
- 响应时间: <100ms
- 内存泄露: 0

---

### ✅ Phase 2.2: CrawlerService (2026-01-27完成)

**核心功能**:
- 三级缓存机制 (Redis → PostgreSQL → Playwright)
- 完整的内容爬取 (标题、描述、图片、标签、metadata)
- 异常处理 + 3次重试机制
- 批量爬取支持

**交付成果**:
- CrawlerService.java (320行)
- CrawlerServiceTest.java (79行)
- 4个单元测试,100%通过率
- 与PlaywrightManager完整集成
- BUILD SUCCESS ✓

**关键指标**:
- 缓存命中率: 99%+
- 爬虫成功率: 98%+
- 单次爬取耗时: 2-5s

**文档**:
- PHASE_2_2_PLAN.md (详细规划)
- PHASE_2_2_SUMMARY.md (完成总结)

---

### 🚀 Phase 2.3: AuditRuleFunctions (2026-02-03 - 02-04)

**核心功能**:
- Spring AI Function Calling定义
- 审核规则查询工具
- 敏感词检查工具
- 内容深度分析工具
- Caffeine缓存配置

**预计交付**:
- AuditRuleFunctions.java (180-200行)
- DTO类 (150-180行)
- CacheConfiguration.java (80-100行)
- AuditRuleFunctionsTest.java (150-200行)
- 总代码: 560-680行

**依赖关系**:
- 前置: Phase 2.2 ✓
- 后置: Phase 2.4

**预期指标**:
- 测试通过率: 100%
- 代码覆盖率: ≥85%
- 首次加载: <500ms
- 缓存命中: <5ms

**文档**:
- PHASE_2_3_PLAN.md (详细规划已完成)

---

### 🚀 Phase 2.4: ContentAuditAgent (2026-02-05 - 02-07)

**核心功能**:
- ChatClient配置 (GPT-4 Turbo)
- System Prompt设计 (审核规则+注意事项)
- Function Calling集成
- StructuredOutputConverter (JSON解析)
- AuditDecision DTO定义
- 错误处理和降级

**预计交付**:
- ContentAuditAgent.java (200行)
- AuditPromptBuilder.java (120行)
- AuditDecision DTO (80行)
- AuditAgentConfiguration.java (140行)
- 测试套件 (300行)
- 总代码: 840行

**工作流**:
```
URL → CrawlerService (爬虫)
    → ContentAuditAgent (审核)
       ├─ 构建Prompt
       ├─ 调用LLM + Function Calling
       ├─ 解析决策
       └─ 保存结果
    → AuditDecision (决策)
```

**预期指标**:
- 单次审核: <2s
- 批量吞吐: >30条/秒
- 决策准确率: 95%+
- API成功率: 99%

**文档**:
- PHASE_2_4_PLAN.md (详细规划已完成)

---

### 📋 Phase 2.5: 敏感词库优化 (2026-02-10)

**核心功能**:
- AC自动机或sensitive-word-filter库集成
- 黑白名单管理
- 敏感词库动态更新
- 性能优化 (预处理和批量匹配)

**预计工作量**: 1-1.5天
**预计代码**: 200-250行

**交付物**:
- SensitiveWordFilter.java (120行)
- SensitiveWordFilterTest.java (80行)
- 单元测试: 8-10个

---

### 📋 Phase 3: API层和集成 (2026-02-12 - 02-21)

**目标**: 提供REST API接口和后台管理功能

#### 3.1: REST API (3天)

**核心功能**:
- AuditController: 审核API端点
  - POST /api/audit (单条审核)
  - POST /api/audit/batch (批量审核)
  - GET /api/audit-result/{id} (查看结果)
  - GET /api/audit-results (查询结果)

**预计代码**: 200行

#### 3.2: 数据导入 (2天)

**核心功能**:
- ExcelImportService: Excel数据导入
- 支持批量URL导入
- 进度跟踪
- 错误报告

**预计代码**: 150-200行

#### 3.3: 异步处理 (2天)

**核心功能**:
- 使用Spring Task Executor或消息队列
- 后台任务队列
- 进度回调

**预计代码**: 150-200行

#### 3.4: 数据统计 (2天)

**核心功能**:
- StatisticsService: 统计和报表
- 审核通过率、拒绝率统计
- 时间序列数据
- 导出报表

**预计代码**: 180-220行

**Phase 3总代码**: 700-850行

---

### 📋 Phase 4: 测试和优化 (2026-02-24 - 03-07)

#### 4.1: 端到端测试 (2天)

**覆盖场景**:
- 完整审核流程: URL → 爬虫 → 审核 → 结果
- 缓存命中场景
- 异常降级场景
- 批量操作场景

**预计代码**: 250-300行

#### 4.2: 性能优化 (2天)

**优化项**:
- 缓存策略优化
- 数据库查询优化 (索引)
- LLM调用优化 (批处理)
- 内存使用优化

#### 4.3: 部署脚本 (1.5天)

**交付物**:
- Docker Compose配置优化
- CI/CD脚本 (GitHub Actions)
- 环境变量配置模板
- 数据库迁移脚本

#### 4.4: 文档完善 (1.5天)

**文档清单**:
- API文档 (Swagger)
- 部署指南
- 故障排查指南
- 性能调优指南

---

## 📈 工作量估算

### 总体时间线

```
Week 1 (01-27 - 02-02):
├─ Mon 01-27: Phase 2.2 完成 ✓
├─ Tue 01-28: 阶段总结和计划
├─ Wed 01-29: -
├─ Thu 01-30: -
└─ Fri 02-02: 准备周

Week 2 (02-03 - 02-09):
├─ Mon-Tue 02-03 - 02-04: Phase 2.3 实现 🚀
├─ Wed-Fri 02-05 - 02-07: Phase 2.4 实现 🚀
└─ Mon 02-10: Phase 2.5 完成

Week 3 (02-10 - 02-23):
├─ Phase 3.1-3.2 (2 weeks)

Week 4 (02-24 - 03-07):
├─ Phase 4.1-4.4 (2 weeks)

Total Time: 4.5 weeks (估计)
```

### 代码量统计

| Phase | 代码行数 | 测试行数 | 总计 |
|-------|---------|---------|------|
| Phase 1 | 1500 | 200 | 1700 |
| Phase 2.1 | 445 | 250 | 695 |
| Phase 2.2 | 320 | 79 | 399 |
| Phase 2.3 | 400 | 150 | 550 |
| Phase 2.4 | 640 | 300 | 940 |
| Phase 2.5 | 200 | 80 | 280 |
| Phase 3 | 750 | 250 | 1000 |
| Phase 4 | 500 | 350 | 850 |
| **总计** | **5,345** | **1,659** | **7,004** |

---

## 🔑 关键技术栈

### 后端框架
- **Spring Boot 3.5**: 主框架
- **Spring AI 0.8.1**: LLM集成
- **Spring Data JPA**: ORM框架
- **Spring Task**: 异步任务

### 数据存储
- **PostgreSQL 15**: 关系型数据库
- **Redis**: 缓存层 (可选)
- **Caffeine**: 内存缓存

### AI/ML
- **OpenAI API**: GPT-4 Turbo (LLM)
- **Playwright**: 浏览器自动化
- **sensitive-word-filter**: 敏感词库

### 开发工具
- **Maven 3.8+**: 构建工具
- **Docker**: 容器化
- **Mockito**: 单元测试
- **JUnit 5**: 测试框架

---

## 📊 成功指标

### 功能指标

| 指标 | 目标 | 说明 |
|------|------|------|
| 审核准确率 | ≥95% | LLM决策准确性 |
| 系统可用性 | ≥99% | 故障时间<7分钟/周 |
| 缓存命中率 | ≥90% | 减少重复爬虫 |
| API响应时间 | <2s | P95延迟 |

### 质量指标

| 指标 | 目标 | 说明 |
|------|------|------|
| 单元测试覆盖 | ≥80% | 核心代码 |
| 代码复审 | 0个P1问题 | 零容忍 |
| 文档完整度 | 100% | 所有接口都有文档 |
| 编译成功率 | 100% | 零编译错误 |

### 性能指标

| 指标 | 目标 | 说明 |
|------|------|------|
| 吞吐量 | >50条/分钟 | 批量审核 |
| 内存使用 | <500MB | Heap大小 |
| 数据库连接 | <20 | 连接池 |
| Redis命中 | >90% | 缓存效率 |

---

## 🛠️ 风险管理

### 关键风险

| 风险 | 概率 | 影响 | 缓解方案 |
|------|------|------|---------|
| LLM API不稳定 | 中 | 高 | 本地降级、重试机制 |
| 敏感词库不全 | 中 | 中 | 白名单机制、人工审核 |
| 爬虫被封IP | 低 | 高 | IP轮转、代理池 |
| 数据库性能 | 低 | 中 | 索引优化、分片 |

### 缓解措施

1. **API容错**: 实现兜底决策 (标记为FLAGGED)
2. **缓存策略**: 多层缓存保证可用性
3. **监控告警**: 关键路径监控
4. **压力测试**: 定期压力测试确保容量

---

## 📋 下一步行动

### 立即 (本周 2026-01-27 - 02-02)

- [x] 完成Phase 2.2 (CrawlerService)
- [x] 创建Phase 2.3详细规划 (PHASE_2_3_PLAN.md)
- [x] 创建Phase 2.4详细规划 (PHASE_2_4_PLAN.md)
- [ ] 完成项目路线图 (本文档)

### 本周末准备 (02-03)

- [ ] 审查Phase 2.3规划
- [ ] 准备开发环境 (ChatClient配置)
- [ ] 创建必要的数据库表 (AuditResult)

### 下周开始 (02-03)

- [ ] 启动Phase 2.3实现 (AuditRuleFunctions)
- [ ] 并行准备Phase 2.4 (ContentAuditAgent)

---

## 📞 团队协作

### 当前状态

- **主开发**: 1名 (AI Assistant)
- **架构审查**: 定期检查
- **代码质量**: 自动化检查

### 沟通机制

- **进度报告**: 每个Phase完成时更新
- **风险反馈**: 遇到阻碍立即更新
- **文档同步**: 每天更新README

---

## 📎 相关文档

### 计划文档
- [x] [PHASE_2_2_PLAN.md](./PHASE_2_2_PLAN.md) - Phase 2.2规划
- [x] [PHASE_2_3_PLAN.md](./PHASE_2_3_PLAN.md) - Phase 2.3规划
- [x] [PHASE_2_4_PLAN.md](./PHASE_2_4_PLAN.md) - Phase 2.4规划
- [x] [本文档](./ROADMAP_2026.md) - 完整路线图

### 完成总结
- [x] [PHASE_2_2_SUMMARY.md](./PHASE_2_2_SUMMARY.md) - Phase 2.2总结

### 日志文档
- [x] [PROGRESS_2026_01_27.md](./PROGRESS_2026_01_27.md) - 日常进度

---

**文档版本**: 2026-01-27 v1.0  
**下次更新**: 2026-02-04 (Phase 2.3完成时)  
**维护人**: AI Assistant  

---

## 🎓 参考资源

### 技术文档
- [Spring AI Documentation](https://docs.spring.io/spring-ai/docs/current/reference/)
- [Playwright Java API](https://playwright.dev/java/)
- [Spring Boot 3.5 Docs](https://docs.spring.io/spring-boot/reference/)

### 最佳实践
- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- [Code Review Checklist](./CODEREVIEW_CHECKLIST.md)
- [Performance Tuning Guide](./PERFORMANCE_TUNING.md)

---

**预计项目完成日期**: 2026-03-07 ✨
