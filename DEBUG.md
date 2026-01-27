# 开发调试指南

## 目录

1. [快速启动](#快速启动)
2. [本地开发环境](#本地开发环境)
3. [调试技巧](#调试技巧)
4. [常见问题](#常见问题)
5. [性能测试](#性能测试)

## 快速启动

### 方式1：使用启动脚本（推荐）

```bash
# 启动开发环境（包含Docker服务）
./scripts/start-dev.sh

# 完整重新构建
./scripts/start-dev.sh build

# 清理环境
./scripts/clean-dev.sh
```

### 方式2：手动启动

```bash
# 1. 启动Docker服务
docker-compose up -d

# 2. 验证服务
docker-compose ps

# 3. Maven构建
mvn clean install

# 4. 运行应用
mvn spring-boot:run
```

## 本地开发环境

### 数据库初始化

应用启动时，Flyway会自动执行迁移脚本。如果需要手动初始化：

```bash
# 查看PostgreSQL容器
docker-compose ps postgres

# 进入PostgreSQL容器
docker-compose exec postgres psql -U postgres -d xhs_audit

# 查看表结构
\d

# 退出
\q
```

### Redis缓存管理

```bash
# 进入Redis容器
docker-compose exec redis redis-cli

# 查看所有键
KEYS *

# 查看特定键的值
GET key_name

# 清空所有数据
FLUSHALL

# 查看内存使用
INFO memory

# 退出
EXIT
```

### PgAdmin GUI管理

访问 http://localhost:5050

- 用户名：admin@admin.com
- 密码：admin

**添加服务器步骤**：
1. 右键"Servers" → "Create" → "Server"
2. 名称：xhs-audit-postgres
3. Connection标签：
   - Host name/address：postgres
   - Username：postgres
   - Password：postgres

### Redis Commander GUI管理

访问 http://localhost:8081

## 调试技巧

### IDE调试模式

#### 使用IntelliJ IDEA

1. **配置远程调试**
   ```bash
   # 在 pom.xml 中确保包含调试配置
   mvn spring-boot:run -Dspring-boot.run.arguments="--debug"
   ```

2. **设置断点**
   - 在源代码中点击行号左侧设置断点
   - 支持条件断点和日志点

3. **调试快捷键**
   - F8：单步执行
   - F7：进入函数
   - Shift+F8：跳出函数
   - F9：继续执行

#### 使用VS Code

```json
// .vscode/launch.json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot App",
      "request": "launch",
      "cwd": "${workspaceFolder}",
      "mainClass": "com.xhs.audit.XhsAuditApplication",
      "projectName": "xhs-audit",
      "preLaunchTask": "maven: clean",
      "args": "",
      "console": "integratedTerminal"
    }
  ]
}
```

### 日志级别调整

编辑 `application.yml`：

```yaml
logging:
  level:
    root: INFO
    com.xhs.audit: DEBUG
    org.springframework.web: DEBUG
    org.springframework.data: DEBUG
    org.hibernate.SQL: DEBUG
```

### 性能分析

#### 使用Spring Boot Actuator

```bash
# 查看应用指标
curl http://localhost:8080/actuator/metrics

# 查看堆内存使用
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 查看HTTP请求统计
curl http://localhost:8080/actuator/metrics/http.server.requests
```

#### 使用JProfiler或YourKit

```bash
# 启用JVM分析
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
```

## 常见问题

### Q1：PostgreSQL连接超时

**症状**：
```
org.postgresql.util.PSQLException: Connection refused
```

**解决方案**：
```bash
# 1. 检查容器状态
docker-compose ps postgres

# 2. 查看日志
docker-compose logs postgres

# 3. 重启服务
docker-compose restart postgres

# 4. 等待30秒后重试
```

### Q2：Redis连接失败

**症状**：
```
redis.clients.jedis.exceptions.JedisConnectionException: Could not get a resource from the pool
```

**解决方案**：
```bash
# 1. 检查Redis健康状态
docker-compose exec redis redis-cli ping

# 2. 检查配置中的Redis地址和端口
grep -A 5 "spring.data.redis" application.yml

# 3. 重启Redis
docker-compose restart redis

# 4. 清除Redis数据
docker-compose exec redis redis-cli FLUSHALL
```

### Q3：Playwright浏览器启动失败

**症状**：
```
Error: Failed to launch browser
```

**解决方案**：
```bash
# 1. 确保系统有足够内存
free -h

# 2. 检查浏览器实例数量
curl http://localhost:8080/actuator/metrics/browser.pool.instances

# 3. 等待浏览器回收（30秒）

# 4. 如需强制关闭
docker-compose restart
```

### Q4：内存占用过高

**症状**：
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**：
```bash
# 1. 检查JVM堆大小配置
export JAVA_OPTS="-Xmx1024m -Xms512m"
mvn spring-boot:run

# 2. 分析内存泄漏
curl http://localhost:8080/actuator/heapdump > heap.hprof

# 3. 使用工具分析（如jhat）
jhat heap.hprof
```

### Q5：Flyway数据库迁移失败

**症状**：
```
ERROR: Flyway failed
```

**解决方案**：
```bash
# 1. 检查数据库连接
docker-compose exec postgres psql -U postgres -d xhs_audit

# 2. 查看迁移历史
SELECT * FROM flyway_schema_history;

# 3. 清理失败的迁移（谨慎操作）
DELETE FROM flyway_schema_history WHERE success = false;

# 4. 重新启动应用
mvn spring-boot:run
```

## 性能测试

### 压力测试工具

#### 1. 使用Apache Bench

```bash
# 单页审核API
ab -n 1000 -c 10 http://localhost:8080/api/audit/content

# 任务进度查询
ab -n 5000 -c 50 http://localhost:8080/api/audit/job/test-job-id
```

#### 2. 使用wrk

```bash
# 安装
brew install wrk

# 测试配置
wrk -t4 -c100 -d30s http://localhost:8080/api/audit/content
```

#### 3. 使用JMeter

```bash
# 下载JMeter
# 创建测试计划，配置线程组和HTTP请求
# 运行测试并分析结果
```

### 基准测试脚本

```bash
#!/bin/bash
# benchmark.sh

echo "=== XHS Audit 性能基准测试 ==="

# 测试1：单页审核延迟
echo "测试1：单页审核延迟..."
time curl -X POST http://localhost:8080/api/audit/content \
  -H "Content-Type: application/json" \
  -d '{"postId":"test","content":"测试内容"}'

# 测试2：缓存命中率
echo "测试2：缓存命中率..."
for i in {1..100}; do
  curl -s http://localhost:8080/api/audit/content/test > /dev/null
done

# 测试3：内存占用
echo "测试3：内存占用..."
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq

# 测试4：Redis内存
echo "测试4：Redis内存..."
docker-compose exec redis redis-cli INFO memory
```

### 监控指标解读

| 指标 | 期望值 | 说明 |
|------|--------|------|
| P99延迟 | < 5s | 99%的请求完成时间 |
| 吞吐量 | 100 req/s | 每秒处理请求数 |
| 错误率 | < 0.1% | 请求失败比率 |
| 缓存命中 | > 70% | Redis缓存有效性 |
| 内存占用 | < 500MB | JVM堆内存使用 |
| GC时间 | < 100ms | 单次垃圾回收停顿 |

## 代码覆盖率

```bash
# 运行测试并生成覆盖率报告
mvn clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

## 持续集成

### GitHub Actions配置

创建 `.github/workflows/ci.yml`：

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: xhs_audit
          POSTGRES_PASSWORD: postgres
      redis:
        image: redis:7-alpine

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build with Maven
        run: mvn clean install -DskipTests
      - name: Run tests
        run: mvn test
      - name: Generate coverage
        run: mvn jacoco:report
```

---

**最后更新**：2026-01-27
