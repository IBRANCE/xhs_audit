# Selenium Grid 迁移指南

## 概述

本项目已从 Playwright 迁移到 Selenium Grid，以支持多线程并发爬取，显著提升性能。

### 迁移原因

- **Playwright 限制**: 基于单个 Node.js 子进程，WebSocket 通道不支持多线程并发
- **性能瓶颈**: 单线程爬取 1000 条数据需要 2.2 小时
- **无法横向扩展**: 多线程会导致 "connection closed" 崩溃

### 迁移收益

- **支持真正的多线程并发**: 4 个 Chrome 节点可同时工作
- **性能提升 4 倍**: 1000 条数据从 2.2 小时降至约 33 分钟
- **可横向扩展**: 支持增加更多节点，理论上可达 8-10 个并发
- **分布式部署**: Selenium Grid 天然支持分布式架构

## 架构变更

### 旧架构 (Playwright)
```
Spring Boot App
    ↓
PlaywrightManager (单线程)
    ↓
Single Node.js Process
    ↓
Chromium Browser (单实例)
```

### 新架构 (Selenium Grid)
```
Spring Boot App
    ↓
SeleniumManager (多线程池)
    ↓
Selenium Grid Hub (localhost:4444)
    ↓ ↓ ↓ ↓
Chrome Node 1  Chrome Node 2  Chrome Node 3  Chrome Node 4
(并发)        (并发)        (并发)        (并发)
```

## 核心组件

### 1. SeleniumManager
- **位置**: [src/main/java/com/xhs/audit/infrastructure/SeleniumManager.java](src/main/java/com/xhs/audit/infrastructure/SeleniumManager.java)
- **功能**:
  - 管理 RemoteWebDriver 连接池
  - 支持多线程并发借用/归还 Driver
  - 自动重连和失败处理
  - 统计和监控

### 2. CrawlerService
- **位置**: [src/main/java/com/xhs/audit/service/CrawlerService.java](src/main/java/com/xhs/audit/service/CrawlerService.java)
- **功能**:
  - 基于 Selenium 的网页抓取
  - 三级缓存 (Redis → PostgreSQL → 抓取)
  - 支持并发批量爬取

### 3. Docker Compose 配置
- **位置**: [docker-compose-selenium.yml](docker-compose-selenium.yml)
- **内容**:
  - 1 个 Selenium Hub
  - 4 个 Chrome Node (可扩展)

## 快速开始

### 1. 启动 Selenium Grid

```bash
./scripts/start-selenium-grid.sh
```

这将启动：
- Selenium Hub: http://localhost:4444/ui
- 4 个 Chrome 节点 (每个支持 1 个并发会话)

### 2. 验证 Grid 状态

浏览器访问: http://localhost:4444/ui

或通过 API:
```bash
curl http://localhost:4444/status | jq
```

### 3. 配置应用

编辑 `src/main/resources/application.yml`:

```yaml
audit:
  crawler:
    selenium:
      grid-url: http://localhost:4444  # Selenium Grid URL
      pool-size: 4                     # Driver 池大小
      page-load-timeout: 30            # 页面加载超时(秒)
      implicit-wait: 10                # 隐式等待(秒)
```

### 4. 启动应用

```bash
./start-local.sh
```

### 5. 测试抓取

```bash
# 单个URL测试
curl -X POST http://localhost:8080/api/v1/audit/crawl \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.xiaohongshu.com/explore/xxx"}'

# 批量测试（并发）
curl -X POST http://localhost:8080/api/v1/audit/batch \
  -F "file=@test-urls.xlsx"
```

## 配置参数说明

### Selenium Grid 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `grid-url` | http://localhost:4444 | Selenium Grid Hub 地址 |
| `pool-size` | 4 | Driver 连接池大小（建议与 Node 数量一致） |
| `page-load-timeout` | 30 | 页面加载超时时间（秒） |
| `implicit-wait` | 10 | 元素查找隐式等待时间（秒） |

### 扩容建议

**本地开发**: 4 个 Node (已配置)
**生产环境**: 8-10 个 Node (修改 `docker-compose-selenium.yml`)

添加更多节点示例:
```yaml
chrome-node-5:
  image: selenium/node-chrome:4.18.1
  container_name: chrome-node-5
  depends_on:
    - selenium-hub
  environment:
    - SE_EVENT_BUS_HOST=selenium-hub
    - SE_EVENT_BUS_PUBLISH_PORT=4442
    - SE_EVENT_BUS_SUBSCRIBE_PORT=4443
    - SE_NODE_MAX_SESSIONS=1
  shm_size: 2gb
  networks:
    - selenium-grid
```

## 性能对比

| 场景 | Playwright (旧) | Selenium Grid (新) | 提升 |
|------|-----------------|---------------------|------|
| 1000 条数据 | 2.2 小时 | 33 分钟 | **4x** |
| 5000 条数据 | 11 小时 | 2.8 小时 | **4x** |
| 并发能力 | 1 线程 | 4 线程 (可扩展) | **4x+** |

## 故障排查

### 1. Selenium Grid 无法启动

**症状**: `./scripts/start-selenium-grid.sh` 超时

**解决**:
```bash
# 检查 Docker
docker ps

# 查看日志
docker-compose -f docker-compose-selenium.yml logs

# 重启
docker-compose -f docker-compose-selenium.yml down
docker-compose -f docker-compose-selenium.yml up -d
```

### 2. 连接 Grid 失败

**症状**: `BrowserPoolExhaustedException: 创建Driver失败`

**检查**:
```bash
# 1. Grid 是否运行
curl http://localhost:4444/status

# 2. 端口是否被占用
lsof -i:4444

# 3. 查看应用日志
tail -f logs/xhs-audit.log | grep Selenium
```

### 3. 性能未提升

**可能原因**:
- `pool-size` 设置过小
- Node 节点数量不足
- 数据库/Redis 成为瓶颈

**优化**:
```yaml
# 增加 pool-size
audit:
  crawler:
    selenium:
      pool-size: 8  # 增加到 8

# 增加数据库连接池
spring:
  datasource:
    hikari:
      maximum-pool-size: 30  # 增加到 30
```

## 监控和日志

### 查看 Grid 状态

Web UI: http://localhost:4444/ui

### 查看应用日志

```bash
# 实时日志
tail -f logs/xhs-audit.log

# 筛选 Selenium 相关
grep "Selenium" logs/xhs-audit.log

# 筛选爬虫相关
grep "爬虫" logs/xhs-audit.log
```

### 健康检查

```bash
curl http://localhost:8080/api/v1/audit/health | jq
```

返回示例:
```json
{
  "status": "UP",
  "components": {
    "selenium": {
      "status": "UP",
      "details": {
        "stats": "SeleniumManager统计 - 活跃实例:2/4, 空闲:2, 等待:0..."
      }
    }
  }
}
```

## 迁移清单

✅ pom.xml - 替换 Playwright 为 Selenium 依赖
✅ SeleniumManager.java - 新建 Selenium Grid 管理器
✅ CrawlerService.java - 迁移到 Selenium API
✅ HealthController.java - 更新健康检查
✅ application.yml - 添加 Selenium Grid 配置
✅ docker-compose-selenium.yml - Selenium Grid 部署配置
✅ scripts/start-selenium-grid.sh - Grid 启动脚本
✅ 备份旧代码 - PlaywrightManager.java.bak, CrawlerServicePlaywright.java.bak

## 旧代码备份

如需回滚到 Playwright:
```bash
cd src/main/java/com/xhs/audit

# 恢复旧文件
mv service/CrawlerServicePlaywright.java.bak service/CrawlerService.java
mv infrastructure/PlaywrightManager.java.bak infrastructure/PlaywrightManager.java

# 删除 Selenium 文件
rm infrastructure/SeleniumManager.java

# 恢复 pom.xml (手动)
```

## 未来优化方向

1. **分布式部署**: 将 Selenium Grid 部署到独立服务器集群
2. **动态扩缩容**: 基于任务队列长度自动调整 Node 数量
3. **更多浏览器**: 支持 Firefox, Edge 等多浏览器测试
4. **会话录制**: 启用 VNC 调试失败的爬取任务

## 参考资料

- [Selenium Grid 官方文档](https://www.selenium.dev/documentation/grid/)
- [Docker Selenium](https://github.com/SeleniumHQ/docker-selenium)
- [MULTI_WORKER_DESIGN.md](MULTI_WORKER_DESIGN.md) - 原始设计文档
