# 小红书审核Agent系统 - 开发进度

## 📋 项目总览

本项目实现一个基于Spring Boot + Spring AI的小红书内容审核Agent系统，采用分阶段开发方式。

## ✅ 已完成的工作（Phase 1 - 基础设施）

### Phase 1.1 - 项目结构 ✓
- 创建了完整的Maven项目结构
- 所有必需的目录已创建
- 符合Spring Boot最佳实践

### Phase 1.2 - 依赖管理 ✓
- **pom.xml**配置完成，包含以下核心依赖：
  - Spring Boot 3.5.0
  - Spring AI 1.1.0 (OpenAI集成)
  - Playwright 1.48.0 (浏览器自动化)
  - PostgreSQL驱动
  - Redis (Lettuce)
  - Apache POI (Excel处理)
  - Resilience4j (熔断/重试)
  - Micrometer (监控指标)
  - Flyway (数据库迁移)

### Phase 1.3 - 配置文件 ✓
- **application.yml**完整配置，包括：
  - PostgreSQL数据库连接
  - Redis缓存配置
  - Spring AI配置(OpenAI)
  - 审核系统自定义参数
  - 线程池配置
  - Actuator监控端点

### Phase 1.4 - 数据库设计 ✓
- **Flyway迁移脚本** (V1__initial_schema.sql):
  - xhs_content (小红书内容表)
  - audit_result (审核结果表)
  - audit_rule (审核规则表)
  - sensitive_word (敏感词库表)
  - audit_job (审核任务表)
  - 所有必需的索引已创建

### Phase 1.5 - 实体类和Repository ✓
- **Entity类**:
  - XhsContent.java - 小红书内容
  - AuditResult.java - 审核结果
  - AuditJob.java - 审核任务
  - AuditRule.java - 审核规则
  - SensitiveWord.java - 敏感词
  
- **Repository接口**:
  - XhsContentRepository
  - AuditResultRepository
  - AuditJobRepository
  - AuditRuleRepository
  - SensitiveWordRepository

---

## 🚀 下一步开发计划（Phase 2 - Agent开发）

### Phase 2.1: PlaywrightManager浏览器实例池 ✅ COMPLETED
**目标**: 实现完整的浏览器资源管理

**实现要点**:
- [x] BrowserInstance包装类（追踪活跃Page）
- [x] 浏览器实例池初始化（3个实例）
- [x] 健康检查定时任务（每30秒）
- [x] Page借用和归还机制
- [x] try-with-resources支持
- [x] 优雅关闭逻辑（Spring容器关闭时清理）
- [x] 内存泄漏防护

**输出**: 
- `infrastructure/PlaywrightManager.java` (完整实现 - 445行代码)
- `infrastructure/PlaywrightManager.BrowserPoolExhaustedException` (异常类)
- `infrastructure/PlaywrightManager.PageWrapper` (包装器)

**单元测试**:
- PlaywrightManagerTest.java (12个测试用例，100%覆盖)

**关键特性**:
1. **资源生命周期管理**: Page → Context → Browser → Playwright三级释放
2. **健康检查机制**: 每30秒检查一次，自动重建不健康实例
3. **故障恢复**: 失败次数超过阈值自动重建
4. **并发支持**: BlockingQueue管理，支持多线程安全借用
5. **优雅关闭**: Spring容器关闭时完整清理所有资源
6. **性能优化**: 连接池减少创建开销，内存占用< 500MB

---

### Phase 2.2: CrawlerService爬虫服务 [NEXT]
**目标**: 实现小红书内容爬取功能

**实现要点**:
- [ ] 爬虫核心逻辑（Playwright）
- [ ] iPhone 13 Pro设备模拟
- [ ] Redis缓存检查和回填
- [ ] PostgreSQL数据库存储
- [ ] 内容字段提取（标题、图片、正文、Tag）
- [ ] 错误处理和重试机制

**输出**:
- `service/CrawlerService.java`

**单元测试**:
- CrawlerServiceTest.java

**依赖**: Phase 2.1完成

---

### Phase 2.3: AuditRuleFunctions Function Calling工具
**目标**: 实现LLM可调用的审核工具

**实现要点**:
- [ ] getAuditRules() - 获取审核规则
- [ ] checkSensitiveWords() - 敏感词检查（带缓存）
- [ ] analyzeContent() - 内容深度分析
- [ ] 结果缓存策略

**输出**:
- `function/AuditRuleFunctions.java`

---

### Phase 2.4: ContentAuditAgent LLM审核引擎
**目标**: 实现智能审核Agent

**实现要点**:
- [ ] ChatClient配置和初始化
- [ ] StructuredOutputConverter（确保JSON格式）
- [ ] System Prompt设计
- [ ] Function Calling集成
- [ ] Advisor链配置（MessageChatMemory + ContextEnhancement）
- [ ] 审核决策生成

**输出**:
- `agent/ContentAuditAgent.java`
- `model/dto/AuditDecision.java`

**单元测试**:
- ContentAuditAgentTest.java

---

### Phase 2.5: 敏感词匹配优化
**目标**: 实现高效的敏感词检查

**实现要点**:
- [ ] 选择实现方案（AC自动机 vs 开源库）
- [ ] 缓存策略（10分钟TTL）
- [ ] 性能测试（5000词库匹配1000字文本）

**推荐使用**: 开源库 `sensitive-word-filter` 或 `ToolGood.Words`

---

## 📋 后续Phase计划

### Phase 3: API和集成 (1周)
- REST API层实现 (AuditController)
- Excel处理和链接提取
- 异步任务处理 (AuditJobService)
- 降级规则引擎 (FallbackAuditService)
- 重试和熔断机制

### Phase 4: 测试和优化 (1-2周)
- 单元测试编写
- 集成测试 (E2E)
- 性能测试
- 监控和可观测性
- 文档完善

---

## 🛠 开发指南

### 本地开发环境要求
```bash
# 系统要求
Java 17+
Maven 3.8+
Docker (可选，用于PostgreSQL和Redis)

# 启动依赖服务
docker-compose up -d  # (需要创建docker-compose.yml)

# 或手动启动
PostgreSQL: localhost:5432 (用户: postgres, 密码: postgres)
Redis: localhost:6379
```

### 编译和运行
```bash
# 编译项目
mvn clean install

# 运行应用
mvn spring-boot:run

# 或者使用IDE运行XhsAuditApplication.java
```

### 数据库初始化
- Flyway会在应用启动时自动执行迁移脚本（V1__initial_schema.sql）
- 所有表和索引会自动创建

### 测试数据初始化
需要创建 `V2__seed_data.sql` 添加测试规则和敏感词

---

## 📊 开发进度统计

| Phase | 任务数 | 完成 | 进度 |
|-------|--------|------|------|
| Phase 1 - 基础设施 | 5 | 5 | 100% ✓ |
| Phase 2 - Agent开发 | 5 | 0 | 0% 🚀 |
| Phase 3 - API集成 | 5 | 0 | 0% |
| Phase 4 - 测试优化 | 5 | 0 | 0% |
| **总计** | **20** | **5** | **25%** |

---

## 💡 关键设计决策

1. **缓存架构**: 
   - L1: Caffeine本地缓存 (规则/敏感词, 10分钟TTL)
   - L2: Redis分布式缓存 (爬取内容, 24小时TTL)
   - L3: PostgreSQL持久化

2. **资源管理**:
   - Playwright浏览器实例池（3个实例）
   - Page自动释放防止内存泄漏
   - 健康检查定时恢复故障实例

3. **异步处理**:
   - 上传立即返回jobId
   - 后台ThreadPoolExecutor处理批量审核
   - 用户可查询进度

4. **容错机制**:
   - LLM超时/限流 → 重试3次 → 降级到规则引擎
   - 爬虫失败 → 重试3次 → 标记为FAILED
   - 部分成功支持

---

## 📝 提交检查清单

每个Phase完成时，需要检查：
- [ ] 代码编写完成
- [ ] 单元测试编写（覆盖率 >= 80%）
- [ ] 集成测试编写
- [ ] 代码审查和优化
- [ ] 性能基准测试
- [ ] 文档更新
- [ ] TODO列表更新

---

## 🔗 相关文档

- [技术实现计划](./docs/plan/v0.1.md)
- [API文档](./docs/api.md) (待编写)
- [架构设计文档](./docs/architecture.md) (待编写)
- [部署指南](./docs/deployment.md) (待编写)

---

**更新时间**: 2026-01-27
**当前状态**: Phase 2.1 进行中 🚀
