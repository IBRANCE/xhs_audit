# XHS Audit 代码实现分析文档

> 生成日期: 2026-01-29
> 分析范围: /Users/bruce/Workspace/code/xhs_audit

## 1. 项目概述

### 1.1 项目简介

XHS Audit (小红书内容审核系统) 是一个基于 LLM 的自动化内容审核系统，通过 Spring AI Function Calling 技术调用大语言模型，对小红书平台的内容进行智能审核。

### 1.2 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 核心框架 | Spring Boot | 3.3.5 |
| 编程语言 | Java | 21 |
| LLM 集成 | Spring AI | 1.0.0-M5 |
| 浏览器自动化 | Playwright | 1.48.0 |
| 数据库 | PostgreSQL | 42.7.3 |
| 缓存 | Redis + Lettuce | - |
| 浏览器池管理 | Caffeine | - |
| Excel 处理 | Apache POI | 5.1.0 |
| 熔断/重试 | Resilience4j | 2.1.0 |
| 监控 | Micrometer + Prometheus | 1.14.0 |
| API 文档 | Springdoc OpenAPI | 2.4.0 |
| 数据库迁移 | Flyway | - |

---

## 2. 整体架构

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Controller Layer                          │
│  (AuditController, FileUploadController, AdminController)        │
├─────────────────────────────────────────────────────────────────┤
│                         Service Layer                            │
│  (ContentAuditService, CrawlerService, ExcelAuditService,        │
│   AsyncAuditService)                                             │
├─────────────────────────────────────────────────────────────────┤
│                         Agent Layer                              │
│  (ContentAuditAgent - LLM 审核引擎)                              │
├─────────────────────────────────────────────────────────────────┤
│                      Function Layer                              │
│  (AuditRuleFunctions - Function Calling 工具)                    │
├─────────────────────────────────────────────────────────────────┤
│                     Infrastructure Layer                         │
│  (PlaywrightManager - 浏览器池管理)                              │
├─────────────────────────────────────────────────────────────────┤
│                      Repository Layer                            │
│  (JPA Repositories - 数据访问)                                   │
├─────────────────────────────────────────────────────────────────┤
│                      Model Layer                                 │
│  (Entities + DTOs)                                               │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 目录结构

```
src/main/java/com/xhs/audit/
├── XhsAuditApplication.java          # 启动类
├── config/                           # 配置类
│   ├── AsyncConfig.java              # 异步线程池配置
│   ├── AuditPromptConfig.java        # Prompt 配置
│   ├── ChatClientConfig.java         # LLM 客户端配置
│   └── OpenApiConfig.java            # OpenAPI 配置
├── controller/                       # REST API 控制器
│   ├── AdminController.java
│   ├── AuditController.java          # 审核 API
│   ├── FileUploadController.java     # 文件上传 API
│   └── HealthController.java
├── service/                          # 业务逻辑服务
│   ├── AsyncAuditService.java        # 异步审核服务
│   ├── ContentAuditService.java      # 内容审核服务
│   ├── CrawlerService.java           # 爬虫服务
│   └── ExcelAuditService.java        # Excel 处理服务
├── agent/                            # AI Agent
│   └── ContentAuditAgent.java        # 内容审核 Agent
├── function/                         # Function Calling 工具
│   ├── AuditRuleFunctions.java       # 审核规则工具
│   └── ContentAnalysisTool.java      # 内容分析工具
├── infrastructure/                   # 基础设施
│   └── PlaywrightManager.java        # 浏览器池管理
├── repository/                       # 数据访问层
│   ├── AuditJobRepository.java
│   ├── AuditResultRepository.java
│   ├── AuditRuleRepository.java
│   ├── SensitiveWordRepository.java
│   └── XhsContentRepository.java
├── model/                            # 数据模型
│   ├── entity/                       # JPA 实体
│   │   ├── AuditJob.java
│   │   ├── AuditResult.java
│   │   ├── AuditRule.java
│   │   ├── SensitiveWord.java
│   │   └── XhsContent.java
│   └── dto/                          # 数据传输对象
│       ├── ApiResponse.java
│       ├── AuditDecision.java
│       ├── AuditRequest.java
│       ├── BatchAuditRequest.java
│       └── JobStatusResponse.java
└── exception/                        # 异常处理
    ├── BusinessException.java
    ├── GlobalExceptionHandler.java
    └── ResourceNotFoundException.java
```

---

## 3. 核心组件详解

### 3.1 内容审核服务 (ContentAuditService)

**文件位置**: `/Users/bruce/Workspace/code/xhs_audit/src/main/java/com/xhs/audit/service/ContentAuditService.java`

**职责**:
- 整合爬虫、Agent 审核、结果存储的完整流程
- 提供 URL 验证和 postId 提取功能
- 管理审核结果缓存（避免重复审核）

**核心方法**:

| 方法名 | 行号 | 描述 |
|--------|------|------|
| `crawlContent(url, forceRefresh)` | 55-77 | 爬取内容（流水线第一阶段） |
| `auditContent(content, jobId)` | 88-119 | 审核内容（流水线第二阶段） |
| `auditContent(url, forceRefresh, jobId)` | 130-147 | 完整审核流程 |
| `extractPostId(url)` | 152-166 | 提取 postId |
| `saveAuditResult(decision, url, jobId)` | 171-187 | 保存审核结果 |
| `convertToDecision(result)` | 192-202 | 转换 AuditResult 为 AuditDecision |

**设计特点**:
- 使用正则表达式提取 postId，支持标准链接和短链接
- `@Transactional` 保证数据一致性
- 缓存命中机制减少重复审核

### 3.2 爬虫服务 (CrawlerService)

**文件位置**: `/Users/bruce/Workspace/code/xhs_audit/src/main/java/com/xhs/audit/service/CrawlerService.java`

**职责**:
- 负责从小红书爬取内容
- 实现三级缓存策略
- 基于 Playwright 的动态爬虫

**三级缓存策略**:

```
┌──────────────────────────────────────────────────────────────┐
│                    crawlContent(url)                         │
├──────────────────────────────────────────────────────────────┤
│  1. Redis 缓存 (热数据)                                       │
│     - Key: xhs:content:{postId}                              │
│     - TTL: 86400 秒 (24小时)                                  │
├──────────────────────────────────────────────────────────────┤
│  2. PostgreSQL (冷数据)                                      │
│     - 查询 xhs_content 表                                    │
├──────────────────────────────────────────────────────────────┤
│  3. Playwright 爬虫 (源数据)                                 │
│     - 页面滚动模拟真实用户行为                                 │
│     - 支持重试机制 (3次重试)                                  │
└──────────────────────────────────────────────────────────────┘
```

**核心方法**:

| 方法名 | 行号 | 描述 |
|--------|------|------|
| `crawlContent(url)` | 67-194 | 主入口，三级缓存 + 爬虫 |
| `crawlWithRetry(url)` | 199-230 | 带重试的爬虫执行 |
| `executeWebScraping(url)` | 235-327 | Playwright 爬虫实现 |
| `simulateUserScrolling(page)` | 474-514 | 模拟真实用户滚动 |
| `validateCrawledContent(content)` | 520-550 | 验证爬取内容 |
| `cleanInvalidContents()` | 636-689 | 清理无效数据 |

**关键配置**:
```java
private static final long CACHE_TTL_SECONDS = 86400;  // 24小时
private static final int MAX_RETRY_ATTEMPTS = 3;       // 最大重试次数
private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000};  // 重试间隔
```

### 3.3 内容审核 Agent (ContentAuditAgent)

**文件位置**: `/Users/bruce/Workspace/code/xhs_audit/src/main/java/com/xhs/audit/agent/ContentAuditAgent.java`

**职责**:
- 核心 AI 审核引擎
- 使用 LLM + Function Calling 实现智能审核
- 并行处理文本和图片审核

**架构设计**:

```
                    ┌─────────────────────────────────────┐
                    │        ContentAuditAgent            │
                    │         auditContent()              │
                    └─────────────────────────────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              │                                               │
              ▼                                               ▼
    ┌─────────────────────┐                         ┌─────────────────────┐
    │   auditTextContent  │                         │    auditImages      │
    │   (LLM 文本审核)     │                         │   (视觉模型审核)     │
    └─────────────────────┘                         └─────────────────────┘
              │                                               │
              │             CompletableFuture.supplyAsync()   │
              └───────────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────▼───────────────────┐
                    │      buildDecisionFromResults()      │
                    │      合并文本和图片审核结果           │
                    └─────────────────────────────────────┘
```

**核心方法**:

| 方法名 | 行号 | 描述 |
|--------|------|------|
| `auditContent(content)` | 112-141 | 审核入口，并行处理文本和图片 |
| `auditTextContent(content)` | 146-169 | 文本内容审核 |
| `auditImages(content)` | 315-422 | 图片内容审核 |
| `buildDecisionFromResults()` | 174-219 | 构建审核决策 |
| `parseDecision(jsonText)` | 250-277 | 解析 LLM 返回的 JSON |
| `extractJson(text)` | 283-307 | 提取 JSON（处理 Markdown 格式） |
| `downloadAndCompressImage()` | 429-475 | 下载并压缩图片（带 Redis 缓存） |
| `compressImage(originalBytes)` | 480-511 | 压缩图片到 128x128 |
| `detectMimeType(bytes)` | 516-535 | 检测图片 MIME 类型 |

**技术特点**:
- 使用 `CompletableFuture` 并行处理文本和图片审核
- 图片使用 Redis 缓存（24小时 TTL）减少重复下载
- 图片压缩到 128x128 像素以减少 token 消耗
- 使用 RestTemplate 直接调用视觉模型 API

### 3.4 浏览器池管理器 (PlaywrightManager)

**文件位置**: `/Users/bruce/Workspace/code/xhs_audit/src/main/java/com/xhs/audit/infrastructure/PlaywrightManager.java`

**职责**:
- 维护多个 Playwright 浏览器实例的生命周期
- 每个 Browser 完全独立，避免 CDP 冲突
- 等待队列机制，超出容量则等待
- 空闲 30 秒自动释放浏览器实例

**架构设计**:

```
┌─────────────────────────────────────────────────────────────────┐
│                      PlaywrightManager                          │
├─────────────────────────────────────────────────────────────────┤
│  activeInstances: Set<BrowserInstance>    活跃的浏览器实例       │
│  idlePool: BlockingQueue<BrowserInstance> 空闲实例队列          │
│  waitingQueue: BlockingQueue<WaitingRequest> 等待队列           │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐ │
│  │                    BrowserInstance                         │ │
│  │  - Browser + BrowserContext + Page 组合                   │ │
│  │  - 同一时刻只允许一个线程操作                               │ │
│  │  - 冷却期机制（2秒）避免 CDP 冲突                          │ │
│  │  - 空闲检测和自动回收                                      │ │
│  └───────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│  后台线程:                                                     │
│  - idleChecker: 空闲检测（30秒间隔）                            │
│  - waiterProcessor: 等待队列处理器（100ms 间隔）                │
└─────────────────────────────────────────────────────────────────┘
```

**关键配置**:

| 配置项 | 值 | 说明 |
|--------|-----|------|
| POOL_SIZE | 10 | 最大浏览器实例数 |
| BORROW_TIMEOUT_SECONDS | 10 | 借用超时时间 |
| MAX_FAILURES_THRESHOLD | 3 | 最大失败次数 |
| IDLE_TIMEOUT_SECONDS | 30 | 空闲超时时间 |
| WAITING_QUEUE_SIZE | 10000 | 等待队列大小 |
| BROWSER_COOLDOWN_MS | 2000 | 浏览器冷却时间 |

**核心方法**:

| 方法名 | 行号 | 描述 |
|--------|------|------|
| `initBrowserPool()` | 248-268 | 初始化浏览器实例池 |
| `createBrowserInstance()` | 274-339 | 创建单个浏览器实例 |
| `borrowPage()` | 347-386 | 从池中借用 Page |
| `closePage(wrapper)` | 467-507 | 关闭 Page 并释放实例 |
| `findIdleInstance()` | 411-421 | 查找空闲浏览器实例 |
| `waitForAvailableInstance()` | 426-462 | 等待获取可用实例 |
| `checkAndReleaseIdleInstances()` | 602-627 | 检查并释放空闲实例 |

### 3.5 异步审核服务 (AsyncAuditService)

**文件位置**: `/Users/bruce/Workspace/code/xhs_audit/src/main/java/com/xhs/audit/service/AsyncAuditService.java`

**职责**:
- 处理批量审核任务的流水线并行执行
- 爬取阶段单线程执行（避免 CDP 冲突）
- 审核阶段多线程并行处理

**流水线模式**:

```
URL 列表
    │
    ├──► CompletableFuture.supplyAsync(crawlExecutor)
    │         │
    │         └── 爬取阶段（单线程）
    │               │
    └──► thenApplyAsync(auditTaskExecutor)
              │
              └── 审核阶段（多线程）
                    │
                    ▼
              收集结果并更新进度
```

**线程池配置** (来自 `AsyncConfig.java`):

| 线程池 | 核心线程 | 最大线程 | 队列容量 | 用途 |
|--------|----------|----------|----------|------|
| taskExecutor | 10 | 50 | 1000 | 主异步任务 |
| crawlExecutor | 1 | 1 | 100 | 爬取专用（单线程） |
| auditTaskExecutor | 5 | 10 | 100 | 审核专用（多线程） |

### 3.6 Excel 审核服务 (ExcelAuditService)

**文件位置**: `/Users/bruce/Workspace/code/xhs_audit/src/main/java/com/xhs/audit/service/ExcelAuditService.java`

**职责**:
- 处理 Excel 文件上传
- 提取小红书链接
- 导出审核结果

**核心方法**:

| 方法名 | 行号 | 描述 |
|--------|------|------|
| `processExcelUpload(file)` | 78-126 | 处理 Excel 上传并创建任务 |
| `extractUrlsFromExcel(inputStream)` | 149-183 | 从 Excel 提取链接 |
| `exportResultsToExcel(jobId)` | 229-358 | 导出审核结果 |
| `downloadTemplate()` | 486-552 | 下载导入模板 |

---

## 4. 数据流与交互模式

### 4.1 单条内容审核流程

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐     ┌──────────────┐
│   Client    │────►│  AuditController │────►│ ContentAuditSvc │────►│  CrawlerSvc  │
│             │     │  /api/v1/audit   │     │                 │     │              │
└─────────────┘     └──────────────────┘     └─────────────────┘     └──────────────┘
                                                                  │
                                                                  ▼
                                                           ┌──────────────┐
                                                           │  Playwright  │
                                                           │  浏览器池    │
                                                           └──────────────┘
                                                                  │
                                                                  ▼
                                                           ┌──────────────┐
                                                           │   Redis      │
                                                           │   缓存       │
                                                           └──────────────┘
                                                                  │
                                                                  ▼
                                                           ┌──────────────┐
                                                           │ PostgreSQL   │
                                                           │   数据库     │
                                                           └──────────────┘
                                                                  │
                           ┌────────────────────────────────────┘
                           ▼
                    ┌──────────────┐     ┌─────────────────┐
                    │ ContentAudit │────►│  ContentAudit   │
                    │    Agent     │     │     Agent       │
                    └──────────────┘     └─────────────────┘
                           │                    │
                           ▼                    ▼
                    ┌──────────────┐     ┌──────────────┐
                    │  LLM API     │     │  Redis 缓存  │
                    │  (文本模型)   │     │  (图片)      │
                    └──────────────┘     └──────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ AuditResult  │
                    │    数据库    │
                    └──────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  ApiResponse │
                    └──────────────┘
```

### 4.2 批量审核流程 (Excel 上传)

```
┌─────────────┐     ┌──────────────────────┐     ┌─────────────────┐
│   Client    │────►│ FileUploadController │────►│ ExcelAuditSvc   │
│             │     │  /api/v1/audit/upload│     │                 │
└─────────────┘     └──────────────────────┘     └─────────────────┘
                                                   │
                                                   ▼
                                            ┌──────────────┐
                                            │ 解析 Excel   │
                                            │ 提取链接     │
                                            └──────────────┘
                                                   │
                                                   ▼
                                            ┌──────────────┐
                                            │  创建任务    │
                                            │ AuditJob     │
                                            └──────────────┘
                                                   │
                                                   ▼
                                            ┌─────────────────┐
                                            │ AsyncAuditSvc   │
                                            │ processAuditJob │
                                            └─────────────────┘
                                                   │
                         ┌─────────────────────────┼─────────────────────────┐
                         │                         │                         │
                         ▼                         ▼                         ▼
                   ┌──────────┐             ┌──────────┐             ┌──────────┐
                   │ 爬取 #1  │             │ 爬取 #2  │             │ 爬取 #N  │
                   │(crawl-*) │             │(crawl-*) │             │(crawl-*) │
                   └────┬─────┘             └────┬─────┘             └────┬─────┘
                        │                        │                        │
                        ▼                        ▼                        ▼
                   ┌──────────┐             ┌──────────┐             ┌──────────┐
                   │ 审核 #1  │             │ 审核 #2  │             │ 审核 #N  │
                   │(audit-*) │             │(audit-*) │             │(audit-*) │
                   └────┬─────┘             └────┬─────┘             └────┬─────┘
                        │                        │                        │
                        └────────────────────────┼────────────────────────┘
                                                  │
                                                  ▼
                                           ┌──────────────┐
                                           │ 更新任务进度 │
                                           │ AuditJob     │
                                           └──────────────┘
```

---

## 5. 关键设计模式

### 5.1 浏览器池模式 (Browser Pool Pattern)

**位置**: `PlaywrightManager.java`

**解决的问题**:
- Playwright 的 CDP (Chrome DevTools Protocol) 在同一 Browser 上不支持并发
- 需要多个独立的 Browser 实例来处理并行请求

**实现要点**:
- 使用 `BlockingQueue` 管理空闲实例
- 每个 `BrowserInstance` 包含完整的 Browser + Context + Page
- `PageWrapper` 实现 `AutoCloseable` 支持 try-with-resources
- 等待队列机制处理超出池容量的请求
- 空闲检测自动回收资源

**代码示例**:
```java
try {
    PlaywrightManager.PageWrapper page = manager.borrowPage();
    page.page.navigate("https://example.com");
    // 爬虫操作
} finally {
    manager.closePage(page);
}
```

### 5.2 LLM Agent 模式 (ContentAuditAgent)

**位置**: `ContentAuditAgent.java`

**架构**:
- 双 ChatClient 设计：`textChatClient` 用于文本，`visionChatClient` 用于图片
- 并行处理文本和图片审核
- Function Calling 工具（未完全启用）
- 图片压缩和缓存优化

### 5.3 三级缓存模式

**位置**: `CrawlerService.crawlContent()`

```
L1: Caffeine (本地缓存) - 10分钟 TTL
L2: Redis (分布式缓存) - 24小时 TTL
L3: PostgreSQL (持久化) - 永久存储
```

### 5.4 流水线并行模式

**位置**: `AsyncAuditService.processAuditJob()`

```
URL ──► CompletableFuture.supplyAsync(crawlExecutor)
           │
           └── thenApplyAsync(auditTaskExecutor)
                 │
                 ▼
           审核结果
```

---

## 6. 数据模型

### 6.1 核心实体

| 实体 | 表名 | 描述 |
|------|------|------|
| `XhsContent` | xhs_content | 小红书内容 |
| `AuditResult` | audit_result | 审核结果 |
| `AuditJob` | audit_job | 审核任务 |
| `AuditRule` | audit_rule | 审核规则 |
| `SensitiveWord` | sensitive_word | 敏感词 |

### 6.2 审核状态流转

```
单条审核:
  正常流程: 爬取 → 审核 → 保存结果

批量审核:
  PENDING → PROCESSING → COMPLETED / PARTIAL_SUCCESS / FAILED

审核结果状态:
  PASSED / REJECTED / UNCERTAIN
```

---

## 7. API 端点

### 7.1 审核相关

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/audit/content` | 单条内容审核 |
| POST | `/api/v1/audit/batch` | 批量审核（同步） |
| GET | `/api/v1/audit/job/{jobId}` | 查询任务进度 |
| GET | `/api/v1/audit/result/{postId}` | 获取审核结果 |

### 7.2 文件管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/audit/upload` | 上传 Excel 文件 |
| GET | `/api/v1/audit/download/{jobId}` | 下载审核结果 |
| GET | `/api/v1/audit/template` | 下载导入模板 |

---

## 8. 配置说明

### 8.1 核心配置项

```yaml
# LLM 配置
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.base-url: ${OPENAI_BASE_URL}
spring.ai.openai.chat.model: qwen/qwen3-4b
spring.ai.openai.vision.model: glm-4.6v-flash

# 爬虫配置
audit.crawler.headless: true

# 图片缓存
audit.image-cache-ttl-hours: 24

# 异步线程池
taskExecutor.core-pool-size: 10
taskExecutor.max-pool-size: 50
crawlExecutor.core-pool-size: 1
auditTaskExecutor.core-pool-size: 5
```

---

## 9. 总结

### 9.1 架构优势

1. **清晰的分层架构**: Controller -> Service -> Agent -> Function -> Repository
2. **高效的浏览器池管理**: 解决了 Playwright CDP 并发问题
3. **智能的缓存策略**: 三级缓存减少重复爬取和计算
4. **灵活的异步处理**: 流水线模式支持批量任务
5. **完善的异常处理**: 全局异常处理器和业务异常

### 9.2 待改进点

1. Function Calling 工具未完全启用
2. 部分重复代码可以抽取
3. 日志格式可进一步标准化

---

*文档版本: 1.0*
*最后更新: 2026-01-29*
