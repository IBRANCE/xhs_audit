# 快速参考卡 (Cheat Sheet)

## 🚀 常用命令

### 环境检查
```bash
# 检查环境是否就绪
./scripts/check-env.sh
```

### Docker 管理
```bash
# 启动PostgreSQL和Redis
./scripts/docker-up.sh

# 停止所有容器
docker-compose down

# 查看容器状态
docker-compose ps

# 查看日志
docker-compose logs -f postgres
docker-compose logs -f redis
```

### 构建项目
```bash
# 快速编译（推荐用于开发）
mvn clean compile

# 完整构建
mvn clean install

# 跳过测试快速构建
mvn clean package -DskipTests
```

### 运行应用
```bash
# 方式1：使用脚本
./scripts/run.sh

# 方式2：使用Maven插件
mvn spring-boot:run

# 方式3：运行JAR
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar

# 调试模式
mvn spring-boot:run -Dspring-boot.run.arguments="--debug"
```

### 测试
```bash
# 运行所有单元测试
mvn test

# 运行特定测试类
mvn test -Dtest=PlaywrightManagerTest

# 生成覆盖率报告
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

### 数据库管理
```bash
# 连接PostgreSQL
docker-compose exec postgres psql -U postgres -d xhs_audit

# 查看表列表
\dt

# 执行SQL查询
SELECT * FROM xhs_content LIMIT 10;

# 退出
\q
```

### Redis 管理
```bash
# 连接Redis
docker-compose exec redis redis-cli

# 查看所有键
KEYS *

# 获取键的值
GET key_name

# 清空所有数据
FLUSHALL

# 查看内存使用
INFO memory

# 退出
EXIT
```

## 📊 项目URL

- **应用**：http://localhost:8080
- **健康检查**：http://localhost:8080/actuator/health
- **指标**：http://localhost:8080/actuator/metrics
- **PgAdmin**：http://localhost:5050 (admin@admin.com / admin)
- **Redis Commander**：http://localhost:8081

## 📁 重要文件和目录

```
xhs-audit/
├── src/main/java/com/xhs/audit/
│   ├── infrastructure/PlaywrightManager.java     # 浏览器实例池
│   ├── model/entity/                             # 数据库实体
│   ├── repository/                               # 数据访问层
│   ├── service/                                  # 业务逻辑
│   └── controller/                               # REST API
├── src/test/java/                                # 单元测试
├── pom.xml                                       # Maven配置
├── docker-compose.yml                            # Docker编排
├── src/main/resources/
│   ├── application.yml                           # 应用配置
│   └── db/migration/                             # Flyway迁移脚本
├── scripts/                                      # 启动脚本
├── docs/plan/v0.1.md                             # 项目计划
└── README.md                                     # 项目文档
```

## 🔧 IDE 配置

### IntelliJ IDEA
```
File → Settings → Build, Execution, Deployment → Compiler → Java Compiler
- Target bytecode version: 17

Run → Edit Configurations
- VM options: -Xmx1024m -Xms512m
```

### VS Code
```
Ctrl+Shift+D → 选择 "Spring Boot App" 配置
```

## 🐛 常见问题快速解决

| 问题 | 解决方案 |
|------|--------|
| PostgreSQL连接失败 | `docker-compose restart postgres && sleep 10` |
| Redis连接失败 | `docker-compose restart redis` |
| Java版本错误 | `java -version` 检查是否为17+，使用 `java -version` 查看 |
| 编译失败 | `mvn clean compile -X` 查看详细日志 |
| 端口占用 | `lsof -i :8080` 找出占用进程 |
| 内存不足 | `export JAVA_OPTS="-Xmx2048m"` |

## 📝 有用的命令组合

### 完整启动流程
```bash
# 1. 检查环境
./scripts/check-env.sh

# 2. 启动Docker
./scripts/docker-up.sh

# 3. 编译
mvn clean compile

# 4. 运行
mvn spring-boot:run
```

### 完整开发循环
```bash
# 编辑代码后
mvn clean compile          # 编译
mvn test                   # 测试
mvn spring-boot:run        # 运行
```

### 清理并重新开始
```bash
./scripts/clean-dev.sh     # 清理本地构建和缓存
docker-compose down        # 停止容器
mvn clean install          # 重新构建
./scripts/docker-up.sh     # 启动Docker
mvn spring-boot:run        # 运行
```

## 🎯 性能优化

### JVM优化
```bash
# 增加堆大小
export JAVA_OPTS="-Xmx2048m -Xms1024m -XX:+UseG1GC"

# 开启垃圾回收日志
export JAVA_OPTS="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
```

### 并发测试
```bash
# 使用Apache Bench
ab -n 1000 -c 10 http://localhost:8080/api/audit

# 使用wrk
wrk -t4 -c100 -d30s http://localhost:8080/api/audit
```

## 📚 文档速查表

- **快速开始**：QUICKSTART.md
- **调试指南**：DEBUG.md
- **API文档**：README.md#api文档
- **开发进度**：DEVELOPMENT.md
- **项目计划**：docs/plan/v0.1.md
- **Phase 2.1总结**：PHASE_2_1_SUMMARY.md

---

**提示**：打印此卡并贴在桌子旁！

最后更新：2026-01-27
