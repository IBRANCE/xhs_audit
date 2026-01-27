# 项目启动指南

## 🚀 快速开始（5分钟）

### 1. 环境检查

```bash
# 检查Java版本（需要17+）
java -version

# 检查Maven版本（需要3.8+）
mvn -version

# 检查Docker（可选，用于本地服务）
docker --version
docker-compose --version
```

### 2. 启动本地服务

```bash
# 进入项目目录
cd /Users/bruce/Workspace/code/xhs_audit

# 启动PostgreSQL和Redis
docker-compose up -d

# 验证服务运行
docker-compose ps
```

**服务列表**：
- PostgreSQL: localhost:5432
- Redis: localhost:6379
- PgAdmin: http://localhost:5050 (可选UI管理工具)
- Redis Commander: http://localhost:8081 (可选UI管理工具)

### 3. 编译项目

```bash
# 使用启动脚本（推荐）
./scripts/start-dev.sh

# 或手动编译
mvn clean compile

# 完整构建
mvn clean install
```

### 4. 配置环境变量

创建 `.env` 文件：

```bash
cat > .env << EOF
OPENAI_API_KEY=sk-your-api-key-here
OPENAI_BASE_URL=https://api.openai.com/v1
EOF
```

### 5. 运行应用

```bash
# 方式1：使用Spring Boot插件
mvn spring-boot:run

# 方式2：运行JAR文件
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar

# 方式3：使用脚本
./scripts/start-dev.sh
```

应用将在 `http://localhost:8080` 启动

## 📊 验证安装

### 检查应用健康状态

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 查看指标
curl http://localhost:8080/actuator/metrics

# 查看应用信息
curl http://localhost:8080/actuator/info
```

### 检查数据库连接

```bash
# 连接PostgreSQL
docker-compose exec postgres psql -U postgres -d xhs_audit

# 查看表
\dt

# 退出
\q
```

### 检查Redis连接

```bash
# 连接Redis
docker-compose exec redis redis-cli

# 查看所有键
KEYS *

# 退出
EXIT
```

## 🔧 开发工作流

### 编辑代码后的步骤

```bash
# 1. 编译
mvn clean compile

# 2. 运行单元测试
mvn test

# 3. 构建项目
mvn clean package

# 4. 启动应用
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar
```

### IDE配置

#### IntelliJ IDEA

1. **打开项目**
   - File → Open... → 选择项目根目录
   - Maven会自动识别并下载依赖

2. **配置运行**
   - Run → Edit Configurations
   - 添加 "Spring Boot" 运行配置
   - Main class: `com.xhs.audit.XhsAuditApplication`
   - VM options: `-Xmx1024m -Xms512m`

3. **调试**
   - 设置断点后按 Shift+F9 启动调试
   - F8 单步执行，F7 进入函数

#### VS Code

1. **安装扩展**
   - Spring Boot Extension Pack
   - Project Manager for Java

2. **打开项目**
   - File → Open Folder → 选择项目根目录

3. **配置调试** (`.vscode/launch.json`)
   - 见DEBUG.md

## 🧪 运行测试

### 单元测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=PlaywrightManagerTest

# 运行特定测试方法
mvn test -Dtest=PlaywrightManagerTest#testBrowserPoolInitialization

# 生成覆盖率报告
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

### 集成测试

```bash
# 运行集成测试（需要Docker服务运行）
mvn clean verify -P integration-tests
```

## 📦 构建和部署

### 构建JAR

```bash
# 完整构建
mvn clean package

# 跳过测试的快速构建
mvn clean package -DskipTests

# 输出文件
# target/xhs-audit-1.0.0-SNAPSHOT.jar
```

### 构建Docker镜像

```bash
# 使用Dockerfile构建
docker build -t xhs-audit:latest .

# 运行容器
docker run -d -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/xhs_audit \
  -e OPENAI_API_KEY=sk-xxx \
  xhs-audit:latest
```

## 🔍 常见问题排查

### Q: 编译失败 - "不支持发行版本 xx"

**解决方案**：
```bash
# 检查Java版本
java -version

# 如果Java < 17，升级Java到17或以上
# macOS使用Homebrew
brew install openjdk@17
export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
```

### Q: 数据库连接失败

**症状**：
```
org.postgresql.util.PSQLException: Connection refused
```

**解决方案**：
```bash
# 1. 检查PostgreSQL容器
docker-compose ps postgres

# 2. 查看日志
docker-compose logs postgres

# 3. 重启容器
docker-compose restart postgres

# 4. 等待30秒后重试
sleep 30
mvn spring-boot:run
```

### Q: Redis连接失败

**症状**：
```
redis.clients.jedis.exceptions.JedisConnectionException
```

**解决方案**：
```bash
# 1. 检查Redis状态
docker-compose exec redis redis-cli ping

# 2. 重启Redis
docker-compose restart redis

# 3. 清空Redis数据
docker-compose exec redis redis-cli FLUSHALL
```

### Q: 内存不足

**症状**：
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**：
```bash
# 增加JVM堆大小
export JAVA_OPTS="-Xmx2048m -Xms1024m"
mvn spring-boot:run

# 或在IDE中配置
# IntelliJ: Run → Edit Configurations → VM options: -Xmx2048m -Xms1024m
```

## 📚 学习资源

- **项目计划**：[docs/plan/v0.1.md](docs/plan/v0.1.md)
- **开发进度**：[DEVELOPMENT.md](DEVELOPMENT.md)
- **调试指南**：[DEBUG.md](DEBUG.md)
- **API文档**：[README.md](README.md#api文档)

## 🆘 获取帮助

### 查看日志

```bash
# 应用日志
tail -f target/spring.log

# Docker日志
docker-compose logs -f postgres
docker-compose logs -f redis

# 特定服务日志
docker-compose logs -f postgres --tail=100
```

### 生成调试信息

```bash
# 生成堆快照
jmap -dump:live,format=b,file=heap.bin <PID>

# 生成线程快照
jstack <PID> > threads.txt

# 获取进程ID
jps

# 分析堆快照
jhat heap.bin
```

## ✅ 检查清单

初次启动时，确保完成以下步骤：

- [ ] Java版本检查（17+）
- [ ] Maven版本检查（3.8+）
- [ ] Docker启动（可选）
- [ ] PostgreSQL容器运行
- [ ] Redis容器运行
- [ ] 项目编译成功
- [ ] 应用启动成功
- [ ] 健康检查通过
- [ ] 数据库连接正常
- [ ] Redis连接正常

---

**遇到问题？**

1. 查看 [DEBUG.md](DEBUG.md) 的常见问题部分
2. 检查 Docker 服务日志
3. 查看应用日志
4. 检查防火墙和端口占用

**最后更新**：2026-01-27
