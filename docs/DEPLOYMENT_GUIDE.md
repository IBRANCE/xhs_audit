# 多节点部署指南

本文档说明如何在生产环境中部署多节点 XHS Audit 系统。

---

## 架构概览

```
┌─────────────┐
│   Nginx     │  负载均衡
│  (可选)     │
└──────┬──────┘
       │
┌──────▼──────────────────────────────────────┐
│                API 节点 (1个)                │
│  - 接收 HTTP 请求                            │
│  - 创建审核任务                              │
│  - 查询任务状态                              │
└──────┬──────────────────────────────────────┘
       │
       │ 发送消息到 Redis Stream
       │
       ▼
┌─────────────────────────────────────────────┐
│              Redis (消息队列)                │
│  - Crawl Stream: 爬取任务队列               │
│  - Audit Stream: 审核任务队列               │
│  - 分布式锁: 防止重复处理                   │
│  - 布隆过滤器: URL 去重                     │
└───┬─────────────────────┬───────────────────┘
    │                     │
    │                     │
┌───▼──────────┐    ┌────▼──────────┐
│ Crawler      │    │ Audit         │
│ Worker-1     │    │ Worker-1      │
│ (爬虫节点)   │    │ (审核节点)    │
└──────────────┘    └───────────────┘
┌──────────────┐    ┌───────────────┐
│ Crawler      │    │ Audit         │
│ Worker-2     │    │ Worker-2      │
│ (爬虫节点)   │    │ (审核节点)    │
└──────────────┘    └───────────────┘
       │                     │
       ▼                     ▼
┌─────────────────────────────────────┐
│       PostgreSQL (共享数据库)        │
│  - audit_job: 任务表                │
│  - xhs_content: 内容表              │
│  - audit_result: 审核结果表         │
└─────────────────────────────────────┘
```

---

## 部署步骤

### 1. 环境准备

#### 1.1 安装 Docker & Docker Compose

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install docker.io docker-compose

# macOS
brew install docker docker-compose

# 启动 Docker
sudo systemctl start docker
sudo systemctl enable docker
```

#### 1.2 配置环境变量

创建 `.env` 文件：

```bash
# .env
OPENAI_API_KEY=your-api-key
OPENAI_BASE_URL=http://your-llm-service:1234/v1
OPENAI_CHAT_MODEL=qwen/qwen3-4b
OPENAI_VISION_MODEL=zai-org/glm-4.6v-flash
```

### 2. 构建镜像

```bash
# 克隆仓库
git clone <repository-url>
cd xhs_audit

# 构建 Docker 镜像
docker build -t xhs-audit:latest .

# 验证镜像
docker images | grep xhs-audit
```

### 3. 初始化数据库

```bash
# 启动数据库（单独）
docker-compose -f docker-compose-production.yml up -d postgres

# 等待数据库启动
sleep 10

# 运行数据库迁移
docker run --rm \
  --network xhs-audit-network \
  -e SPRING_PROFILES_ACTIVE=production \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/xhs_audit \
  -e DATABASE_USER=postgres \
  -e DATABASE_PASSWORD=postgres \
  xhs-audit:latest \
  java -jar app.jar --spring.flyway.enabled=true

# 验证表结构
docker exec -it xhs-audit-postgres psql -U postgres -d xhs_audit -c "\dt"
```

### 4. 启动所有服务

```bash
# 启动完整集群
docker-compose -f docker-compose-production.yml up -d

# 查看服务状态
docker-compose -f docker-compose-production.yml ps

# 查看日志
docker-compose -f docker-compose-production.yml logs -f
```

### 5. 验证部署

#### 5.1 检查服务健康状态

```bash
# API 节点
curl http://localhost:8080/actuator/health

# Redis
docker exec -it xhs-audit-redis redis-cli ping

# PostgreSQL
docker exec -it xhs-audit-postgres pg_isready -U postgres

# Selenium Grid
curl http://localhost:4444/status
```

#### 5.2 检查 Worker 运行状态

```bash
# 查看 Crawler Worker 1 日志
docker logs xhs-audit-crawler-1 --tail 50

# 查看 Audit Worker 1 日志
docker logs xhs-audit-audit-1 --tail 50

# 检查 Redis Stream 消费者组
docker exec -it xhs-audit-redis redis-cli XINFO GROUPS crawl:tasks
docker exec -it xhs-audit-redis redis-cli XINFO GROUPS audit:tasks
```

#### 5.3 测试 API

```bash
# 提交审核任务
curl -X POST http://localhost:8080/api/audit/submit \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.xiaohongshu.com/explore/xxx",
    "forceRefresh": false
  }'

# 查询任务状态
curl http://localhost:8080/api/audit/status/{jobId}
```

---

## 扩缩容

### 水平扩展

#### 增加 Crawler Worker

```bash
# 修改 docker-compose-production.yml，添加：
crawler-worker-3:
  image: xhs-audit:latest
  container_name: xhs-audit-crawler-3
  environment:
    SPRING_PROFILES_ACTIVE: crawler
    HOSTNAME: crawler-node-3
    # ... 其他配置与 crawler-worker-1 相同

# 重新部署
docker-compose -f docker-compose-production.yml up -d crawler-worker-3
```

#### 增加 Audit Worker

```bash
# 类似地添加 audit-worker-3
audit-worker-3:
  image: xhs-audit:latest
  container_name: xhs-audit-audit-3
  environment:
    SPRING_PROFILES_ACTIVE: audit
    HOSTNAME: audit-node-3
    # ... 其他配置

docker-compose -f docker-compose-production.yml up -d audit-worker-3
```

### 垂直扩展

修改 Docker Compose 中的资源限制：

```yaml
deploy:
  resources:
    limits:
      cpus: '4'        # 增加 CPU
      memory: 4G       # 增加内存
    reservations:
      cpus: '2'
      memory: 2G
```

---

## 监控

### Prometheus 指标

访问 Prometheus UI：http://localhost:9090

关键指标：
- `crawl_task_success_total`: 爬取成功总数
- `crawl_task_failed_total`: 爬取失败总数
- `audit_task_success_total`: 审核成功总数
- `crawl_processing_duration_seconds`: 爬取处理时长

### Grafana 仪表盘

访问 Grafana UI：http://localhost:3000
- 用户名: `admin`
- 密码: `admin`

导入仪表盘模板：
1. 点击 "+" → "Import"
2. 上传 `monitoring/grafana/dashboards/xhs-audit.json`

### 日志聚合

使用 ELK Stack（可选）：

```bash
# 安装 Filebeat（每个节点）
docker run -d \
  --name filebeat \
  --volume="$(pwd)/filebeat.yml:/usr/share/filebeat/filebeat.yml:ro" \
  --volume="/var/lib/docker/containers:/var/lib/docker/containers:ro" \
  --volume="/var/run/docker.sock:/var/run/docker.sock:ro" \
  docker.elastic.co/beats/filebeat:8.11.0
```

---

## 故障排查

### 问题 1: Worker 无法连接到 Redis

**症状:**
```
Failed to connect to Redis: Connection refused
```

**解决:**
```bash
# 检查 Redis 是否运行
docker ps | grep redis

# 检查网络连通性
docker exec xhs-audit-crawler-1 ping -c 3 redis

# 检查 Redis 配置
docker exec xhs-audit-redis redis-cli CONFIG GET bind
```

### 问题 2: 任务积压

**症状:**
```
# Redis Stream Pending 消息数量持续上升
XPENDING crawl:tasks xhs-audit-workers
```

**解决:**
1. 增加 Worker 数量
2. 调整批量消费大小
3. 检查是否有 Worker 卡死

```bash
# 查看 Pending 消息详情
docker exec -it xhs-audit-redis redis-cli \
  XPENDING crawl:tasks xhs-audit-workers - + 10

# 重启卡死的 Worker
docker restart xhs-audit-crawler-1
```

### 问题 3: 分布式锁超时

**症状:**
```
[CrawlerWorker] 获取分布式锁失败: jobId=xxx
```

**解决:**
1. 检查 Redis 性能
2. 调整锁超时时间
3. 检查任务处理时长

```bash
# 查看 Redis 慢日志
docker exec xhs-audit-redis redis-cli SLOWLOG GET 10

# 查看锁持有情况（Redisson）
docker exec xhs-audit-redis redis-cli KEYS "crawler:lock:*"
```

---

## 性能优化

### 1. Redis 调优

```bash
# 增加最大连接数
docker exec xhs-audit-redis redis-cli CONFIG SET maxclients 10000

# 启用持久化
docker exec xhs-audit-redis redis-cli CONFIG SET appendonly yes
```

### 2. PostgreSQL 调优

```sql
-- 增加连接池
ALTER SYSTEM SET max_connections = 200;

-- 调整共享缓冲区
ALTER SYSTEM SET shared_buffers = '2GB';

-- 调整工作内存
ALTER SYSTEM SET work_mem = '16MB';

-- 重启生效
docker restart xhs-audit-postgres
```

### 3. Selenium Grid 调优

```yaml
# 增加 Chrome Node 数量
selenium-chrome-3:
  image: selenium/node-chrome:4.24.0
  environment:
    SE_NODE_MAX_SESSIONS: 6  # 增加并发会话数
```

---

## 备份与恢复

### 数据库备份

```bash
# 自动备份脚本
docker exec xhs-audit-postgres pg_dump -U postgres xhs_audit > backup_$(date +%Y%m%d).sql

# 定时备份（cron）
0 2 * * * /path/to/backup.sh
```

### 数据恢复

```bash
# 恢复数据库
docker exec -i xhs-audit-postgres psql -U postgres xhs_audit < backup_20260203.sql
```

---

## 安全建议

1. **使用强密码**: 修改默认的数据库密码
2. **网络隔离**: 使用 Docker 网络隔离服务
3. **TLS 加密**: 启用 Redis 和 PostgreSQL 的 TLS
4. **API 认证**: 添加 JWT 或 OAuth2 认证
5. **限流**: 使用 Nginx 或 API Gateway 限流

---

## 成本估算

**单节点配置:**
- API: 2 CPU, 2GB RAM
- Crawler Worker: 2 CPU, 2GB RAM × 2
- Audit Worker: 2 CPU, 3GB RAM × 2
- PostgreSQL: 2 CPU, 4GB RAM
- Redis: 1 CPU, 2GB RAM
- Selenium Grid: 4 CPU, 8GB RAM

**总计: 17 CPU, 26GB RAM**

**云服务参考 (AWS):**
- EC2 (t3.xlarge × 3): ~$300/月
- RDS PostgreSQL (db.t3.large): ~$150/月
- ElastiCache Redis (cache.t3.medium): ~$80/月

**总成本: ~$530/月**

---

## 联系与支持

- 问题报告: [GitHub Issues]
- 技术文档: `docs/`
- 监控指南: `docs/MONITORING.md`

---

## 更新日志

- **v4.0**: 多节点支持、分布式锁、布隆过滤器
- **v3.0**: Redis Stream 消息队列
- **v2.0**: Selenium Grid 集成
- **v1.0**: 初始版本
