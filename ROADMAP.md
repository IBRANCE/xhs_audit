# 开发周计划 (Development Roadmap)

**项目**：小红书内容审核Agent系统  
**当前阶段**：Phase 2.2 - Phase 4  
**时间周期**：2026年1月27日 - 2026年2月27日（4周）

---

## 📅 第1周：Phase 2.2 - CrawlerService爬虫服务

### 目标
实现小红书内容爬取功能，基于PlaywrightManager进行并发爬虫

### 任务清单

#### Day 1-2: 爬虫核心逻辑（2天）
- [ ] **CrawlerService.java** (主服务类)
  - 注入PlaywrightManager
  - 实现爬取逻辑框架
  - 错误处理和重试

- [ ] **爬虫工具方法**
  - getPostContent(url) - 爬取帖子内容
  - getPostMetadata(url) - 提取发布者、时间、点赞等
  - getComments(url) - 提取评论（可选）

#### Day 3: 缓存集成（1天）
- [ ] **缓存检查逻辑**
  - Redis检查（24小时TTL）
  - PostgreSQL查询
  - 缓存回填

#### Day 4: 数据库存储（1天）
- [ ] **数据持久化**
  - 内容保存到XhsContent表
  - JSONB字段处理（图片URL、Tag、元数据）
  - 事务管理

#### Day 5: 单元测试和优化（1天）
- [ ] **CrawlerServiceTest.java** (12个测试用例)
  - 缓存命中/未命中场景
  - 爬虫成功/失败场景
  - 并发爬虫测试
  - 性能基准测试

**输出**：
- CrawlerService.java (~300行)
- CrawlerServiceTest.java (~250行)
- 爬虫性能基准数据

**验收标准**：
- 单个URL爬取延迟 < 5秒
- 并发3个URL吞吐量 > 100 URL/分钟
- 缓存命中率 > 70%
- 测试覆盖率 >= 80%

---

## 📅 第2周：Phase 2.3 & 2.4 - 规则引擎和LLM Agent

### 目标
实现审核规则和LLM智能审核引擎

### 任务清单

#### Day 1-2: AuditRuleFunctions Function Calling工具（2天）
- [ ] **AuditRuleFunctions.java** (Function定义)
  - getAuditRules() - 从数据库获取规则列表
  - checkSensitiveWords(text) - 敏感词检查
  - analyzeContent(content) - 内容分析
  - 缓存策略（Caffeine 10分钟TTL）

- [ ] **规则缓存优化**
  - 规则列表缓存
  - 敏感词库缓存
  - 缓存更新机制

#### Day 3-4: ContentAuditAgent LLM引擎（2天）
- [ ] **ContentAuditAgent.java** (Agent实现)
  - ChatClient配置和初始化
  - System Prompt设计（审核指引）
  - Function Calling集成
  - StructuredOutputConverter（确保JSON）

- [ ] **AuditDecision.java** (审核决策DTO)
  - isViolation: 是否违规
  - violations: 违规类别列表
  - riskLevel: 风险等级 (LOW/MEDIUM/HIGH)
  - confidence: 置信度 (0-1)
  - explanation: 详细说明

- [ ] **Advisor链配置**
  - MessageChatMemory (多轮对话记忆)
  - ContextEnhancementAdvisor (上下文增强)

#### Day 5: 单元测试和调试（1天）
- [ ] **ContentAuditAgentTest.java** (15个测试用例)
  - 规则获取功能
  - 敏感词检测
  - LLM决策生成
  - 缓存有效性
  - 多轮对话测试

**输出**：
- AuditRuleFunctions.java (~200行)
- ContentAuditAgent.java (~350行)
- AuditDecision.java (~100行)
- 测试文件 (~300行)

**验收标准**：
- 单次审核延迟 < 3秒（P99）
- 规则缓存命中率 > 90%
- LLM调用成功率 > 99%
- 测试覆盖率 >= 85%

---

## 📅 第3周：Phase 2.5 & Phase 3 - 敏感词优化和API层

### 目标
优化敏感词匹配性能，实现REST API和业务流程集成

### 任务清单

#### Day 1-2: 敏感词优化（2天）
- [ ] **选择实现方案**
  - 评估AC自动机 vs sensitive-word-filter库
  - 性能基准比对

- [ ] **SensitiveWordMatcher.java**
  - 使用开源库实现（推荐sensitive-word-filter）
  - Caffeine缓存（10分钟TTL）
  - 批量匹配支持
  - 性能优化（目标：1000字文本 < 1ms）

#### Day 3-4: REST API实现（2天）
- [ ] **AuditController.java**
  - POST /api/audit/content - 单条内容审核
  - POST /api/audit/upload - Excel/CSV文件上传
  - GET /api/audit/job/{jobId} - 查询任务进度
  - GET /api/audit/results - 查询审核结果

- [ ] **ContentAuditService.java** (业务流程)
  - 单条审核流程编排
  - 缓存优化
  - 异常处理

- [ ] **AuditJobService.java**
  - 任务创建和进度追踪
  - 批量处理异步执行

#### Day 5: 集成测试（1天）
- [ ] **集成测试**
  - API端到端测试
  - 缓存命中率验证
  - 并发压力测试
  - 性能基准测试

**输出**：
- SensitiveWordMatcher.java (~150行)
- AuditController.java (~200行)
- ContentAuditService.java (~250行)
- AuditJobService.java (~200行)
- 集成测试 (~300行)

**验收标准**：
- 敏感词匹配：1000字 < 1ms
- API响应时间 < 5秒（P99）
- 吞吐量 > 100 req/s
- 缓存命中率 > 75%

---

## 📅 第4周：Phase 3完成和Phase 4 - 测试和优化

### 目标
完成Excel处理、异步任务、以及全面的测试和性能优化

### 任务清单

#### Day 1-2: Excel处理和异步任务（2天）
- [ ] **ExcelImportService.java**
  - 使用Apache POI解析Excel
  - 链接提取正则表达式精化
  - 批量导入流程

- [ ] **AsyncAuditProcessor.java**
  - ThreadPoolExecutor异步处理
  - 批量任务协调
  - 进度更新机制

- [ ] **FallbackAuditService.java**（降级服务）
  - LLM失败时使用规则引擎
  - 部分成功处理

#### Day 3: 单元测试和覆盖率（1天）
- [ ] **补充单元测试**
  - ExcelImportServiceTest
  - AsyncAuditProcessorTest
  - FallbackAuditServiceTest
  - 目标覆盖率 >= 80%

- [ ] **覆盖率报告生成**
  ```bash
  mvn clean test jacoco:report
  ```

#### Day 4-5: 性能优化和文档（2天）
- [ ] **性能测试和优化**
  - JVM参数调优
  - 数据库查询优化
  - 缓存策略微调
  - 基准测试报告

- [ ] **可观测性和监控**
  - Actuator端点完善
  - Micrometer指标配置
  - 健康检查优化
  - 日志系统完善

- [ ] **文档更新**
  - API文档 (Swagger/Javadoc)
  - 部署指南
  - 架构设计文档
  - 故障排查指南

**输出**：
- ExcelImportService.java (~200行)
- AsyncAuditProcessor.java (~250行)
- FallbackAuditService.java (~150行)
- 测试文件 (~400行)
- 完整文档更新

**验收标准**：
- 单元测试覆盖率 >= 80%
- 集成测试通过率 100%
- 性能目标达成：
  - 单条审核 < 5s
  - 批量处理 1000条 < 10分钟
  - 吞吐量 > 200 req/s
  - 内存占用 < 1GB

---

## 🎯 关键性能指标 (KPI)

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 单条审核延迟 | < 5s (P99) | 包括爬虫、LLM、缓存 |
| 批量处理吞吐 | 1000条/10分钟 | 1000个URL并发3爬虫 |
| 缓存命中率 | > 75% | Redis + Caffeine |
| LLM调用成功率 | > 99% | 重试3次 |
| 可用性 | > 99% | 故障降级策略 |
| 测试覆盖率 | >= 80% | 单元 + 集成测试 |
| 内存占用 | < 1GB | 包含缓存 |
| 响应时间P99 | < 5s | API端点 |

---

## 📊 里程碑

| 时间 | 阶段 | 交付物 | 状态 |
|------|------|--------|------|
| 1月27日 | Phase 2.1 | PlaywrightManager完成 | ✅ |
| 2月03日 | Phase 2.2 | CrawlerService完成 | 🚀 |
| 2月10日 | Phase 2.3-2.4 | Agent引擎完成 | 📋 |
| 2月17日 | Phase 2.5 & 3 | API层完成 | 📋 |
| 2月24日 | Phase 4 | 测试优化完成 | 📋 |
| 2月28日 | 项目完成 | 全部验收 | 📋 |

---

## 🔧 技术栈更新

基于前期审核，推荐的技术选择：

1. **敏感词库**: `sensitive-word-filter` （更简单，性能接近）
2. **缓存策略**: Caffeine(本地) + Redis(分布式) + PostgreSQL(持久)
3. **异步处理**: Spring @Async + ThreadPoolExecutor
4. **降级策略**: LLM失败 → 规则引擎
5. **监控**: Spring Boot Actuator + Micrometer + Prometheus

---

## ⚠️ 风险和应对

| 风险 | 影响 | 应对 |
|------|------|------|
| LLM API限流 | 审核延迟增加 | 实现队列 + 重试机制 |
| 爬虫被反爬 | 爬取失败 | 代理池 + 延迟随机化 |
| 内存溢出 | 系统崩溃 | Page限制 + 定时清理 |
| 缓存不一致 | 数据错误 | Redis+DB同步 + TTL设置 |
| 性能不达标 | 交付延迟 | 提前基准测试 + 早期优化 |

---

## 📝 每日更新清单

每天工作结束时检查：
- [ ] 代码提交了吗？
- [ ] 单元测试通过了吗？
- [ ] 覆盖率有提升吗？
- [ ] 有新的TODO待处理吗？
- [ ] 文档更新了吗？
- [ ] 性能基准可接受吗？

---

**更新时间**: 2026年1月27日  
**下一次更新**: 2026年2月3日
