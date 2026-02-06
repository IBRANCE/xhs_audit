# XHS Audit - 小红书内容审核系统

这是一个基于Spring Boot + Spring AI + Playwright的自动化内容审核系统，用于小红书内容的智能审核和风险检测。

## 功能特性

- 🤖 **LLM驱动审核**：基于Spring AI和Function Calling实现智能审核引擎
- 🌐 **Web爬虫**：使用Playwright自动化爬取小红书内容
- 📊 **批量处理**：支持Excel/CSV文件上传，异步批量审核
- 🔍 **多维度检测**：违规判定、违规类别、风险等级、详细说明
- ⚡ **性能优化**：三级缓存架构 + 浏览器实例池 + 敏感词AC自动机
- 🛡️ **容错机制**：熔断器 + 重试策略 + 优雅降级
- 📈 **任务追踪**：实时进度更新 + 审核统计 + 规则管理

## 技术栈

### 后端框架
- **Spring Boot 3.5.0**：现代Java应用框架
- **Spring AI 1.1.0**：LLM集成框架
- **Spring Data JPA**：ORM框架

### 数据存储
- **PostgreSQL 15**：主存储库（Entity + 审核结果 + 敏感词库）
- **Redis 7**：分布式缓存（24小时TTL）
- **Caffeine**：本地缓存（10分钟TTL）

### 自动化和爬虫
- **Playwright**：浏览器自动化（3实例池 + 健康检查）
- **HtmlUnit**：XML解析

### LLM和AI
- **OpenAI API**：Function Calling能力
- **Spring AI**：Chat消息处理 + 工具调用

### 工具库
- **Apache POI**：Excel处理
- **Resilience4j**：熔断器和重试机制
- **Flyway**：数据库版本管理
- **Lombok**：代码生成
- **Jackson**：JSON处理

### 开发工具
- **Maven 3.8+**：项目构建
- **Docker & Docker Compose**：本地开发环境

## 📚 项目规划和文档

### 开发者必读

- 📖 [项目路线图 (ROADMAP_2026.md)](./ROADMAP_2026.md) - 完整的项目规划 (Phase 1-4)
- 📋 [文档导航 (docs/plan/README.md)](./docs/plan/README.md) - 规划文档快速导航
- 🎯 [本周工作计划 (WEEKLY_PLAN_WEEK2.md)](./docs/plan/WEEKLY_PLAN_WEEK2.md) - 2026-02-03至02-09的详细工作安排

### 实现阶段规划

- [Phase 2.3: AuditRuleFunctions](./docs/plan/PHASE_2_3_PLAN.md) - 审核规则函数定义 (280行规划)
- [Phase 2.4: ContentAuditAgent](./docs/plan/PHASE_2_4_PLAN.md) - LLM审核智能体 (350行规划)
- [Phase 2.2: CrawlerService (已完成)](./docs/plan/PHASE_2_2_PLAN.md) - Web爬虫服务
- [Phase 2.2: 完成总结](./PHASE_2_2_SUMMARY.md) - 爬虫实现总结

### 项目状态

- 📊 [进度报告 (PROGRESS_2026_01_27.md)](./PROGRESS_2026_01_27.md) - 最新进度 (37.5% 完成)
- ✅ [规划完成清单 (PLANNING_CHECKLIST.md)](./PLANNING_CHECKLIST.md) - 规划工作验收
- 📈 [规划总结 (PLANNING_COMPLETION_SUMMARY.md)](./PLANNING_COMPLETION_SUMMARY.md) - 规划质量评估 (96.9%)

## 🏗️ 物理架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           生产环境分布式部署架构                                │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │    负载均衡      │
                              │   (Nginx可选)   │
                              │    :80/443      │
                              └────────┬────────┘
                                       │
                              ┌────────▼────────┐
                              │   API Server    │◄──── HTTP API 入口
                              │  xhs-audit-api  │      任务创建/查询
                              │  :8080          │
                              └────────┬────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
           ┌────────▼────────┐ ┌───────▼───────┐ ┌───────▼───────┐
           │ CrawlerWorker-1 │ │ AuditWorker-1 │ │ CrawlerWorker-2│
           │  :8080          │ │  :8080        │ │  :8080         │
           │  爬虫节点       │ │  审核节点      │ │  爬虫节点      │
           └────────┬────────┘ └───────┬───────┘ └────────┬────────┘
                    │                  │                  │
                    └──────────────────┼──────────────────┘
                                       │
    ┌──────────────────────────────────┼──────────────────────────────────┐
    │                                  │                                  │
┌───▼────────────┐          ┌────────▼────────┐          ┌────────▼────────────┐
│   Redis        │          │  PostgreSQL      │          │  Selenium Grid     │
│  :6379         │          │   :5432          │          │   :4444            │
│                │          │                  │          │                    │
│  • Stream队列  │          │  • audit_job     │          │  • Hub (调度中心)  │
│    - crawl     │          │  • xhs_content   │          │  x x x x      │
│    - audit     │          │  • audit_result  │          │  • Firefox ×8      │
│  • 分布式锁    │          │  • audit_rule    │          │                    │
│  • 布隆过滤器  │          │  • sensitive_word│          │                    │
│  • 缓存        │          │                  │          │                    │
└────────────────┘          └──────────────────┘          └─────────────────────┘
    │
    │                                  │
    └──────────────────────────────────┘
                                       │
                                       ▼
                              ┌─────────────────────────────────────┐
                              │              AI 模型层               │
                              └──────────────────┬──────────────────┘
                                                 │
                    ┌────────────────────────────┼────────────────────────────┐
                    │                            │                            │
           ┌────────▼────────┐          ┌────────▼────────┐          ┌────────▼────────┐
           │   语言模型        │          │   图片识别模型   │          │   Function      │
           │                  │          │                  │          │   Calling       │
           │  • OpenAI GPT   │          │  • GLM-4.6v    │          │   规则引擎      │
           │  • Qwen3-4b    │          │  • GPT-4o     │          │                  │
           │  • 文本理解     │          │  • 图像审核     │          │  • 违规判定     │
           │  • 语义分析     │          │  • OCR识别      │          │  • 风险分类     │
           └─────────────────┘          └─────────────────┘          └─────────────────┘
                    │                            │                            │
                    └────────────────────────────┼────────────────────────────┘
                                                 │
                              ┌──────────────────┴──────────────────┐
                              │              输出格式                 │
                              │  • 违规判定 (isViolation)          │
                              │  • 违规类别 (violations[])         │
                              │  • 风险等级 (riskLevel)            │
                              │  • 置信度 (confidence)              │
                              │  • 详细说明 (explanation)          │
                              └─────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                           开发环境 All-in-One 部署                             │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │  xhs-audit-app  │
                              │  :8080          │
                              │                 │
                              │  • API Server  │────► HTTP API
                              │  • CrawlerWorker│
                              │  • AuditWorker │
                              └────────┬────────┘
                                       │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
     ┌────────▼────────┐     ┌────────▼────────┐     ┌────────▼────────┐
     │   Redis         │     │  PostgreSQL     │     │  Selenium Grid  │
     │  :6379          │     │   :5432        │     │   :4444         │
     │  (消息队列+缓存) │     │  (主数据库)     │     │  (浏览器集群)   │
     └─────────────────┘     └────────────────┘     └─────────────────┘
                                       │
                                       ▼
                              ┌─────────────────────────────────────┐
                              │              AI 模型层               │
                              │  ┌─────────────┐  ┌─────────────┐  │
                              │  │  语言模型    │  │ 图片识别模型 │  │
                              │  │ Qwen3-4b   │  │ GLM-4.6v   │  │
                              │  └─────────────┘  └─────────────┘  │
                              └─────────────────────────────────────┘
```

### 组件说明

| 组件 | 容器名 | 端口 | CPU | 内存 | 职责 |
|------|--------|------|-----|------|------|
| **应用层** |
| API Server | xhs-audit-api | 8080 | 2核 | 2GB | HTTP API 入口、任务管理、结果查询 |
| Crawler Worker | xhs-audit-crawler-* | 8080 | 2核 | 2GB | 内容爬取、页面解析、去重过滤 |
| Audit Worker | xhs-audit-audit-* | 8080 | 2核 | 3GB | AI 审核、规则匹配、风险评估 |
| **数据层** |
| PostgreSQL | xhs-audit-postgres | 5432 | 2核 | 2GB | 主数据库、持久化存储、事务处理 |
| Redis | xhs-audit-redis | 6379 | 1核 | 1GB | 缓存、消息队列、分布式锁 |
| **爬虫层** |
| Selenium Hub | selenium-hub | 4444 | 1核 | 1GB | 浏览器调度中心 |
| Chrome Node | selenium-chrome-* | - | 1核 | 1GB | 页面渲染（默认4实例） |
| Firefox Node | selenium-firefox-* | - | 1核 | 1GB | 页面渲染（默认8实例） |
| **AI 模型层** |
| 语言模型 | 外部API | - | - | - | 文本理解、语义分析、违规判定（Function Calling） |
| 图片识别模型 | 外部API | - | - | - | 图像审核、OCR识别、敏感内容检测 |

### 部署模式

| 模式 | 适用场景 | 配置方式 |
|------|---------|---------|
| **All-in-One** | 开发调试、本地测试 | `WORKER_TYPE=both` |
| **分离部署** | 小规模生产环境 | API + Crawler Worker + Audit Worker |
| **分布式部署** | 大规模高并发 | 多节点 Worker + 负载均衡 |

### 数据流

```
用户提交请求
     │
     ▼
┌──────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   API Server │────▶│ Redis Stream    │────▶│ CrawlerWorker   │
│  (任务创建)  │     │ xhs:stream:crawl│     │  (爬取内容)     │
└──────────────┘     └─────────────────┘     └────────┬────────┘
                                                       │
                                                       ▼
                                              ┌─────────────────┐
                                              │ Selenium Grid    │
                                              │  (页面渲染)      │
                                              │  • 截图         │
                                              │  • DOM解析       │
                                              └────────┬────────┘
                                                       │
                                                       ▼
                                              ┌─────────────────┐
                                              │ PostgreSQL      │
                                              │ (保存内容+截图)  │
                                              └────────┬────────┘
                                                       │
                                                       ▼
                                              ┌─────────────────┐
                                              │ Redis Stream    │
                                              │ xhs:stream:audit│
                                              └────────┬────────┘
                                                       │
                                                       ▼
                                              ┌─────────────────┐
                                              │ AuditWorker     │
                                              │  (AI 审核)      │
                                              └────────┬────────┘
                                                       │
                                    ┌──────────────────┼──────────────────┐
                                    │                  │                  │
                                   ▼                  ▼                  ▼
                          ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
                          │  语言模型    │    │ 图片识别模型 │    │ 规则引擎    │
                          │  Qwen3-4b  │    │ GLM-4.6v   │    │ Function    │
                          │  • 文本审核 │    │  • 图像审核 │    │  Calling   │
                          │  • 语义分析 │    │  • OCR识别  │    │  • 违规判定 │
                          └─────────────┘    └─────────────┘    │  • 风险分类 │
                                    │                  │        └─────────────┘
                                    └──────────────────┘                  │
                                                       │                  │
                                                       ▼                  │
                                              ┌─────────────────────────────┐
                                              │       审核结果输出           │
                                              │  isViolation, violations[], │
                                              │  riskLevel, confidence,     │
                                              │  explanation               │
                                              └────────┬────────────────────┘
                                                       │
                                                       ▼
                                              ┌─────────────────┐
                                              │ PostgreSQL      │
                                              │ (保存结果)      │
                                              └─────────────────┘
```

### 外部依赖服务

| 服务类型 | 模型 | 用途 | 配置变量 |
|---------|------|------|---------|
| **语言模型** | `qwen/qwen3-4b` | 文本理解、语义分析、违规判定 | `OPENAI_CHAT_MODEL` |
| **图片识别模型** | `glm-4.6v-flash` / `gpt-4o` | 图像审核、OCR识别、敏感内容检测 | `OPENAI_VISION_MODEL` |
| **Function Calling** | 内置规则引擎 | 标准化审核输出格式 | - |

> ⚠️ **注意**：LLM 服务为外部依赖，需要配置有效的 API Key 才能正常工作。

**配置示例**：
```bash
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_CHAT_MODEL=qwen/qwen3-4b          # 语言模型
OPENAI_VISION_MODEL=glm-4.6v-flash       # 图片识别模型
```

## 🚀 快速开始

### 前置条件

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | 项目使用 Java 21 |
| Maven | 3.8+ | 项目构建工具 |
| Docker | 20.x+ | 容器化运行 |
| Docker Compose | 2.x+ | 服务编排 |

### 环境要求

- **内存**：推荐 8GB+（Selenium Grid 需要约 4GB）
- **磁盘**：推荐 50GB+（浏览器缓存 + 数据库）
- **网络**：能访问小红书域名（xiaohongshu.com）

### 部署步骤

#### 方式一：Docker Compose（推荐用于开发）

```bash
# 1. 克隆项目
cd /Users/bruce/Workspace/code/xhs_audit

# 2. 配置环境变量
cat > .env << EOF
OPENAI_API_KEY=sk-your-api-key-here
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_CHAT_MODEL=qwen/qwen3-4b
WORKER_TYPE=both
EOF

# 3. 启动基础设施
docker-compose up -d

# 4. 查看服务状态
docker-compose ps

# 5. 构建并运行应用
mvn clean package -DskipTests
java -jar target/xhs-audit-*.jar
```

#### 方式二：本地运行（开发调试）

```bash
# 1. 先启动 Docker 服务
docker-compose up -d postgres redis selenium-hub

# 2. 运行应用（使用本地配置）
mvn spring-boot:run
```

#### 方式三：生产部署

```bash
# 1. 构建 Docker 镜像
docker build -t xhs-audit:latest .

# 2. 使用生产配置启动
docker-compose -f docker-compose-production.yml up -d

# 3. 验证健康状态
curl http://localhost:8080/actuator/health
```

### 服务访问

| 服务 | URL | 用途 |
|------|-----|------|
| 应用接口 | http://localhost:8080 | REST API 入口 |
| Swagger UI | http://localhost:8080/swagger-ui.html | API 文档 |
| Selenium Grid | http://localhost:4444 | 浏览器集群控制台 |
| PgAdmin | http://localhost:5050 | PostgreSQL 管理界面 |
| Redis Commander | http://localhost:8081 | Redis 管理界面 |

### 验证部署

```bash
# 1. 检查应用健康
curl -s http://localhost:8080/actuator/health | jq .

# 2. 检查 Selenium Grid
curl -s http://localhost:4444/status | jq '.ready'

# 3. 提交测试任务
curl -X POST http://localhost:8080/api/audit/async \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.xiaohongshu.com/explore/test", "forceRefresh": false}'

# 4. 查询任务状态
curl http://localhost:8080/api/audit/status/{jobId}
```

## 项目结构

```
xhs-audit/
├── src/main/java/com/xhs/audit/
│   ├── XhsAuditApplication.java          # Spring应用入口
│   ├── config/                           # Spring配置类
│   ├── controller/                       # REST API控制器
│   ├── service/                          # 业务逻辑层
│   ├── agent/                            # LLM Agent引擎
│   ├── function/                         # Function Calling工具
│   ├── infrastructure/                   # 资源管理（Playwright等）
│   ├── repository/                       # 数据访问层
│   ├── model/
│   │   ├── entity/                       # JPA实体类
│   │   └── dto/                          # 数据传输对象
│   ├── advisor/                          # Advisor链（AI增强）
│   ├── exception/                        # 异常处理
│   └── util/                             # 工具函数
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   └── db/migration/                     # Flyway数据库脚本
├── docker-compose.yml                    # Docker服务编排
└── pom.xml                               # Maven依赖配置
```

## 核心模块说明

### 1. PlaywrightManager（浏览器管理）
- 维护3个浏览器实例池
- 自动故障恢复和健康检查
- PageWrapper包装器确保资源正确释放

### 2. CrawlerService（爬虫服务）
- 支持单页和批量爬取
- 三级缓存查询：Redis → PostgreSQL → 爬虫
- JSONB字段存储半结构化数据

### 3. ContentAuditAgent（审核Agent）
- 基于Spring AI的多轮对话
- Function Calling工具调用
- StructuredOutputConverter保证JSON输出

### 4. AuditRuleFunctions（审核规则工具）
- 多维度规则检查（违规判定/类别/等级）
- 敏感词匹配（AC自动机优化）
- 规则引擎和降级策略

### 5. REST API层
- 内容上传和批量导入
- 任务进度查询
- 审核结果检索

## 性能指标

| 指标 | 目标值 | 说明 |
|------|-------|------|
| 单页审核延迟 | < 5s | P99延迟 |
| 批量处理吞吐 | 100条/分钟 | 包括爬虫 |
| 内存占用 | < 500MB | Playwright 3实例 + Redis缓存 |
| 缓存命中率 | > 70% | Redis 24小时 + Caffeine 10分钟 |
| 敏感词匹配 | < 1ms | 5000词库 |

## 开发进度

详见 [DEVELOPMENT.md](DEVELOPMENT.md)

**当前进度**：Phase 2.1 PlaywrightManager实现进行中

- ✅ Phase 1：基础设施完成（项目结构、配置、Entity、Repository）
- 🚀 Phase 2：Agent开发进行中
  - 2.1 PlaywrightManager
  - 2.2 CrawlerService
  - 2.3 AuditRuleFunctions
  - 2.4 ContentAuditAgent
  - 2.5 敏感词优化
- ⏳ Phase 3：API集成
- ⏳ Phase 4：测试和优化

## API文档

### 上传内容审核

```bash
POST /api/audit/upload
Content-Type: multipart/form-data

file: (Excel/CSV文件)
```

**响应**
```json
{
  "jobId": "uuid",
  "message": "任务已提交，请查询进度"
}
```

### 查询任务进度

```bash
GET /api/audit/job/{jobId}
```

**响应**
```json
{
  "jobId": "uuid",
  "status": "PROCESSING",
  "total": 100,
  "completed": 45,
  "succeeded": 42,
  "failed": 3,
  "progress": 45
}
```

### 查询审核结果

```bash
GET /api/audit/results?jobId={jobId}&page=0&size=10
```

**响应**
```json
{
  "content": [
    {
      "postId": "xxx",
      "title": "内容标题",
      "content": "内容文本",
      "isViolation": true,
      "violations": ["垃圾广告", "虚假宣传"],
      "riskLevel": "HIGH",
      "confidence": 0.92,
      "explanation": "检测到虚假宣传关键词..."
    }
  ],
  "totalElements": 50,
  "totalPages": 5
}
```

## 故障排查

### PostgreSQL连接失败
```bash
# 检查服务状态
docker-compose ps

# 查看日志
docker-compose logs postgres

# 重启服务
docker-compose restart postgres
```

### Redis连接失败
```bash
# 检查Redis健康状态
docker-compose logs redis

# 清除Redis数据
docker-compose exec redis redis-cli FLUSHALL
```

### 内存占用过高
- 检查Playwright浏览器实例数
- 查看Redis内存使用情况：`redis-cli INFO memory`
- 检查PostgreSQL连接池配置

## 贡献指南

1. Fork本项目
2. 创建特性分支：`git checkout -b feature/xxx`
3. 提交更改：`git commit -am 'Add feature xxx'`
4. 推送到分支：`git push origin feature/xxx`
5. 创建Pull Request

## 许可证

MIT License

## 联系方式

- 项目管理员：Bruce
- 技术讨论：GitHub Issues

---

**最后更新**：2026-01-27
