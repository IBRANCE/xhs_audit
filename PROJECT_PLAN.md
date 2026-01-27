# XHS Audit 开发计划（2026-01-27）

**项目状态**: Phase 2.1 ✅ 完成  
**当前进度**: 已完成 Phase 1 + Phase 2.1，准备启动 Phase 2.2  
**目标完成**: 2026-02月底

---

## 📊 开发阶段规划

### ✅ Phase 1: 项目基础设施 (已完成)

- [x] 项目结构创建
- [x] pom.xml 完整配置
- [x] 数据库设计和 Flyway 迁移脚本
- [x] 5个 Entity 类
- [x] 5个 Repository 接口
- [x] application.yml 配置文件
- [x] Docker Compose 编排
- [x] 开发文档编写

---

### 🚀 Phase 2: Agent 引擎开发

#### Phase 2.1: PlaywrightManager (✅ 已完成)
- [x] 浏览器实例池实现 (445行代码)
- [x] 12个单元测试 (100%覆盖)
- [x] 健康检查和自动恢复
- [x] 优雅关闭机制
- [x] try-with-resources 支持

---

#### Phase 2.2: CrawlerService 爬虫服务 (🎯 本周任务)

**目标**: 实现完整的小红书内容爬取功能

**子任务**:

**2.2.1: CrawlerService 基础实现** (预计 1天)
- [ ] 创建 `service/CrawlerService.java`
- [ ] 注入 PlaywrightManager
- [ ] 实现爬虫核心方法
  - [ ] `crawlPost(url)` - 爬取单个帖子
  - [ ] `crawlPosts(urls)` - 批量爬取
- [ ] 内容字段提取
  - [ ] 标题、正文、图片URL
  - [ ] 发布者信息、时间、点赞数
  - [ ] Tag 列表
- [ ] 错误处理和重试机制 (3次重试)

**2.2.2: 缓存层集成** (预计 0.5天)
- [ ] Redis 缓存检查 (24小时 TTL)
- [ ] Caffeine 本地缓存 (10分钟 TTL)
- [ ] 缓存回填逻辑
- [ ] PostgreSQL 持久化存储
- [ ] 缓存策略测试

**2.2.3: 单元和集成测试** (预计 1天)
- [ ] 编写 CrawlerServiceTest
  - [ ] 单页爬取测试
  - [ ] 批量爬取测试
  - [ ] 缓存命中测试
  - [ ] 错误重试测试
- [ ] 集成测试 (需要实际网络)
- [ ] 性能基准测试 (吞吐量、延迟)
- [ ] 并发测试 (3并发)

**时间估算**: 2-3天  
**依赖**: ✅ PlaywrightManager 已完成

---

#### Phase 2.3: AuditRuleFunctions 审核规则引擎 (🎯 下周任务)

**目标**: 实现审核规则的函数化调用

**子任务**:

**2.3.1: 规则引擎基础** (预计 1.5天)
- [ ] 创建 `function/AuditRuleFunctions.java`
- [ ] 定义 Function 方法签名
  - [ ] `@Tool` 标记
  - [ ] 参数和返回类型定义
- [ ] 实现审核规则函数
  - [ ] `checkViolation(content)` - 违规判定
  - [ ] `getViolationCategory(content)` - 违规分类
  - [ ] `assessRiskLevel(content)` - 风险等级评估
  - [ ] `generateExplanation(result)` - 生成解释

**2.3.2: 规则库和缓存** (预计 1day)
- [ ] 加载审核规则 (从 AuditRule 表)
- [ ] 规则缓存策略
- [ ] 规则动态更新机制
- [ ] 规则优先级和权重配置

**2.3.3: 单元测试** (预计 1day)
- [ ] 各个规则函数的测试
- [ ] 规则组合测试
- [ ] 缓存刷新测试

**时间估算**: 3-4天  
**依赖**: CrawlerService 完成

---

#### Phase 2.4: ContentAuditAgent LLM 审核 Agent (🎯 后续任务)

**目标**: 实现 Spring AI 驱动的多轮对话审核引擎

**子任务**:

**2.4.1: Agent 框架** (预计 1.5day)
- [ ] 创建 `agent/ContentAuditAgent.java`
- [ ] 集成 Spring AI ChatClient
- [ ] 实现消息构建和发送
- [ ] Function Calling 集成
  - [ ] 绑定 AuditRuleFunctions
  - [ ] Tool 选择和调用

**2.4.2: 输出解析** (预计 1day)
- [ ] StructuredOutputConverter 实现
- [ ] JSON 输出验证
- [ ] AuditResult 对象构建
- [ ] 错误恢复机制

**2.4.3: 容错机制** (预计 0.5day)
- [ ] Resilience4j 集成
  - [ ] 熔断器配置
  - [ ] 重试策略 (3次)
  - [ ] 超时配置 (300秒)
- [ ] 降级服务 (FallbackAuditService)

**2.4.4: 单元测试** (预计 1day)
- [ ] Agent 消息流测试
- [ ] Function Calling 测试
- [ ] 输出解析测试
- [ ] 容错机制测试

**时间估算**: 4-5天  
**依赖**: AuditRuleFunctions 完成

---

#### Phase 2.5: 敏感词优化 (🎯 后续任务)

**目标**: 优化敏感词匹配性能 (50ms → 0.5ms)

**子任务**:

**2.5.1: AC 自动机集成** (预计 1day)
- [ ] 集成开源库 (sensitive-word-filter 或实现 AC 自动机)
- [ ] 敏感词库加载
- [ ] 敏感词初始化和编译

**2.5.2: 缓存策略** (预计 0.5day)
- [ ] Caffeine 缓存 (10分钟 TTL)
- [ ] 缓存预热
- [ ] 动态更新机制

**2.5.3: 性能测试** (预计 0.5day)
- [ ] 基准测试对比 (原方案 vs AC自动机)
- [ ] 并发压力测试
- [ ] 内存占用分析

**时间估算**: 2-3天  
**依赖**: Phase 2.4 的一部分

---

### 📡 Phase 3: API 和集成层

#### Phase 3.1: REST API 接口 (🎯 后续任务)

**目标**: 完整的 REST API 实现

**子任务**:

**3.1.1: 上传和查询接口** (预计 1.5day)
- [ ] `POST /api/audit/upload` - 上传 Excel/CSV 文件
  - [ ] 文件验证
  - [ ] 内容解析
  - [ ] 异步任务提交
- [ ] `GET /api/audit/job/{jobId}` - 查询任务进度
- [ ] `GET /api/audit/results` - 查询审核结果
- [ ] `POST /api/audit/content` - 单个内容审核

**3.1.2: 错误处理和限流** (预计 1day)
- [ ] 全局异常处理器 (GlobalExceptionHandler)
- [ ] 限流 (Rate Limit) 配置
- [ ] 请求验证
- [ ] 统一响应格式

**3.1.3: 文档** (预计 0.5day)
- [ ] Swagger/OpenAPI 文档
- [ ] 请求/响应示例

**时间估算**: 3-4天  
**依赖**: Phase 2 基本完成

---

#### Phase 3.2: Excel 批量处理 (🎯 后续任务)

**目标**: 支持 Excel/CSV 文件批量审核

**子任务**:

**3.2.1: 文件解析** (预计 0.5day)
- [ ] 创建 `service/ExcelProcessorService.java`
- [ ] 使用 Apache POI 解析 Excel
- [ ] CSV 文件支持
- [ ] 数据验证

**3.2.2: 批量处理** (预计 1day)
- [ ] 创建 ExcelProcessorService
- [ ] 批次处理逻辑
- [ ] 进度报告
- [ ] 错误记录

**时间估算**: 1.5-2天  
**依赖**: ContentAuditAgent 完成

---

#### Phase 3.3: 异步任务处理 (🎯 后续任务)

**目标**: 使用消息队列和异步任务

**子任务**:

**3.3.1: 异步架构** (预计 1day)
- [ ] 创建 `service/AsyncAuditService.java`
- [ ] 使用 @Async 处理
- [ ] ThreadPool 配置
- [ ] 任务监控

**3.3.2: 任务队列** (预计 0.5day)
- [ ] Redis 队列 (可选升级)
- [ ] 任务持久化
- [ ] 优先级队列

**时间估算**: 1.5-2天  
**依赖**: 前序 API 层

---

### 🧪 Phase 4: 测试和优化

#### Phase 4.1: 单元测试完善 (🎯 后续任务)

**目标**: 达到 80%+ 覆盖率

**时间估算**: 2-3天

---

#### Phase 4.2: 集成和 E2E 测试 (🎯 后续任务)

**目标**: 完整流程测试

**时间估算**: 2-3天

---

#### Phase 4.3: 性能优化 (🎯 后续任务)

**目标**: 单页 <5s，批量 100条/分钟

**时间估算**: 1-2天

---

#### Phase 4.4: Docker 容器化 (🎯 后续任务)

**目标**: 完整的容器化部署

**时间估算**: 1day

---

## 📅 时间表

| 周次 | 日期 | Phase | 任务 | 状态 |
|-----|------|-------|------|------|
| W5 | 1/27-1/31 | 2.1 | PlaywrightManager | ✅ 完成 |
| W5 | 1/27-1/31 | 2.2 | CrawlerService | 🎯 本周 |
| W6 | 2/3-2/7 | 2.3 | AuditRuleFunctions | 📅 计划 |
| W6 | 2/3-2/7 | 2.4 | ContentAuditAgent | 📅 计划 |
| W7 | 2/10-2/14 | 2.5 | 敏感词优化 | 📅 计划 |
| W7 | 2/10-2/14 | 3.1 | REST API | 📅 计划 |
| W8 | 2/17-2/21 | 3.2-3.3 | Excel/异步 | 📅 计划 |
| W9 | 2/24-2/28 | 4.x | 测试和优化 | 📅 计划 |

---

## 🎯 本周目标 (W5: 1/27-1/31)

### 已完成
- ✅ Phase 2.1: PlaywrightManager 完全实现
- ✅ 12个单元测试通过
- ✅ Docker 环境配置
- ✅ 完整开发文档

### 本周任务 (2-3天工作量)
- [ ] Phase 2.2.1: CrawlerService 基础实现
  - [ ] 爬虫核心方法
  - [ ] 内容字段提取
- [ ] Phase 2.2.2: 缓存层集成
  - [ ] Redis 和 Caffeine
- [ ] Phase 2.2.3: 单元测试

### 预计完成日期
- **CrawlerService**: 1月 30-31日

---

## 🛠️ 技术选型确认

| 组件 | 选择 | 理由 |
|------|------|------|
| 浏览器 | Playwright | 性能好，支持 Chromium |
| 缓存 | Redis + Caffeine | 分布式 + 本地双层 |
| LLM | Spring AI + OpenAI | 原生 Spring 集成 |
| 数据库 | PostgreSQL | 性能强，JSONB 支持 |
| 异步 | @Async + ThreadPool | Spring 原生支持 |
| 容错 | Resilience4j | 功能完整 |
| 敏感词 | AC 自动机 | 性能最优 |

---

## 📋 每个 Phase 的 Definition of Done

### DoD 标准
- [ ] 代码实现完成 (功能 100%)
- [ ] 单元测试通过 (覆盖率 ≥80%)
- [ ] 代码审查通过
- [ ] 文档完整
- [ ] 与上层模块集成成功
- [ ] 性能测试通过

---

## 🚦 进度跟踪

### 每日检查清单

```
[ ] 代码编译无误
[ ] 单元测试通过
[ ] 集成测试通过
[ ] 文档同步更新
[ ] TODO 列表更新
```

### 每周评审

```
每周五：
- 代码审查
- 性能分析
- 问题解决
- 下周计划确认
```

---

## 💡 风险预防

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|--------|
| Playwright 稳定性 | 低 | 高 | 充分的单元测试、故障恢复 |
| LLM API 超时 | 中 | 高 | Resilience4j 熔断、降级 |
| 数据库性能 | 低 | 中 | 索引优化、查询优化 |
| 缓存不一致 | 中 | 中 | 明确的缓存策略、TTL 设置 |
| 并发问题 | 低 | 高 | 充分的并发测试 |

---

## 📞 支持和联系

- **问题追踪**: 在代码中使用 TODO 注释
- **文档**: 查看 DEVELOPMENT.md 和 DEBUG.md
- **测试**: 运行 `mvn test` 验证

---

**更新时间**: 2026-01-27 14:30:00  
**下次审视**: 2026-02-03 (每周一)
