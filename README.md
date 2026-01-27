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

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.8+
- Docker & Docker Compose

### 安装步骤

1. **克隆项目**
   ```bash
   cd /Users/bruce/Workspace/code/xhs_audit
   ```

2. **启动依赖服务**
   ```bash
   docker-compose up -d
   ```
   
   服务列表：
   - PostgreSQL：localhost:5432
   - Redis：localhost:6379
   - PgAdmin：http://localhost:5050
   - Redis Commander：http://localhost:8081

3. **配置环境变量**
   ```bash
   # 创建 .env 文件
   cat > .env << EOF
   OPENAI_API_KEY=sk-your-api-key-here
   OPENAI_BASE_URL=https://api.openai.com/v1
   EOF
   ```

4. **构建项目**
   ```bash
   mvn clean install
   ```

5. **运行应用**
   ```bash
   mvn spring-boot:run
   ```
   
   应用将在 http://localhost:8080 启动

### 查看数据库和缓存

- **PgAdmin（PostgreSQL）**：http://localhost:5050
  - 用户名：admin@admin.com
  - 密码：admin

- **Redis Commander**：http://localhost:8081

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
