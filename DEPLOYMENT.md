# 小红书审核系统 - 本地部署与测试指南

## 🎯 当前状态

### ✅ 已完成
- 所有56个单元测试通过 (54个运行,2个跳过)
- 应用成功启动在 http://localhost:8080
- 数据库连接正常 (PostgreSQL)
- Redis连接正常
- 浏览器池初始化成功 (3个Playwright实例)
- 健康检查端点正常 (`/actuator/health`)

### 📊 系统组件状态
```json
{
  "status": "UP",
  "components": {
    "db": "UP",          // PostgreSQL 15.15
    "redis": "UP",       // Redis 8.4.0  
    "diskSpace": "UP",   // 665GB free
    "ping": "UP",
    "ssl": "UP"
  }
}
```

## 🚀 快速启动

### 方式1: 使用启动脚本 (推荐)

```bash
cd /Users/bruce/Workspace/code/xhs_audit
./start.sh
```

该脚本会自动:
- ✅ 检查Java环境 (需要JDK 17+)
- ✅ 检查PostgreSQL连接
- ✅ 启动Redis (如果未运行)
- ✅ 检查Playwright浏览器
- ✅ 停止旧进程 (如果端口8080被占用)
- ✅ 后台启动应用
- ✅ 等待启动完成并显示健康状态

### 方式2: 手动启动

```bash
# 1. 确保PostgreSQL运行
psql -U postgres -d xhs_audit -c "SELECT version();"

# 2. 确保Redis运行  
redis-cli ping

# 3. 后台启动应用
mvn spring-boot:run > /tmp/xhs_audit.log 2>&1 &

# 4. 等待10秒后检查
sleep 10
curl http://localhost:8080/actuator/health | jq '.'
```

## 🧪 API测试

### 运行测试脚本

```bash
./test-api.sh
```

### 手动API测试

#### 1. 健康检查
```bash
curl http://localhost:8080/actuator/health | jq '.'
```

**预期响应**: 所有组件状态为UP

#### 2. 单条内容审核
```bash
curl -X POST http://localhost:8080/api/v1/audit/content \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.xiaohongshu.com/explore/test123",
    "forceRefresh": false
  }' | jq '.'
```

#### 3. 批量审核
```bash
curl -X POST http://localhost:8080/api/v1/audit/batch \
  -H "Content-Type: application/json" \
  -d '{
    "links": [
      "https://www.xiaohongshu.com/explore/1",
      "https://www.xiaohongshu.com/explore/2"
    ]
  }' | jq '.'
```

#### 4. Excel文件上传 (需要准备test-posts.xlsx)
```bash
curl -X POST http://localhost:8080/api/v1/file/upload \
  -F "file=@test-posts.xlsx" \
  -F "taskName=测试任务-$(date +%Y%m%d-%H%M%S)" \
  -v
```

#### 5. 查询任务状态
```bash
# 替换{jobId}为实际任务ID
curl http://localhost:8080/api/v1/audit/job/{jobId} | jq '.'
```

#### 6. 查询审核结果
```bash
# 替换{postId}为实际帖子ID
curl http://localhost:8080/api/v1/audit/result/{postId} | jq '.'
```

#### 7. 下载审核结果
```bash
# 替换{jobId}为实际任务ID
curl -O -J http://localhost:8080/api/v1/file/download/{jobId}
```

## 📋 可用API端点

### Actuator端点
- `GET /actuator/health` - 健康检查
- `GET /actuator/info` - 应用信息
- `GET /actuator/metrics` - 指标监控
- `GET /actuator/loggers` - 日志配置

### 审核API
- `POST /api/v1/audit/content` - 单条内容审核
- `POST /api/v1/audit/batch` - 批量审核
- `GET /api/v1/audit/job/{jobId}` - 查询任务状态
- `GET /api/v1/audit/result/{postId}` - 查询审核结果

### 文件API
- `POST /api/v1/file/upload` - Excel文件上传
- `GET /api/v1/file/download/{jobId}` - 下载审核结果

## 🗂️ 数据库操作

### 查看所有表
```bash
psql -U postgres -d xhs_audit -c "\dt"
```

预期输出:
```
              List of relations
 Schema |       Name        | Type  |  Owner   
--------+-------------------+-------+----------
 public | audit_job         | table | postgres
 public | audit_result      | table | postgres
 public | audit_rule        | table | postgres
 public | sensitive_word    | table | postgres
 public | xhs_content       | table | postgres
```

### 查询审核任务
```bash
psql -U postgres -d xhs_audit -c "
  SELECT job_id, task_name, status, total_links, completed_count 
  FROM audit_job 
  ORDER BY created_at DESC 
  LIMIT 5;
"
```

### 查询审核结果
```bash
psql -U postgres -d xhs_audit -c "
  SELECT post_id, audit_status, confidence_score, model_name 
  FROM audit_result 
  ORDER BY audited_at DESC 
  LIMIT 10;
"
```

### 清空测试数据
```bash
psql -U postgres -d xhs_audit <<EOF
TRUNCATE TABLE audit_result CASCADE;
TRUNCATE TABLE xhs_content CASCADE;
TRUNCATE TABLE audit_job CASCADE;
-- 保留审核规则和敏感词表
EOF
```

## 🔍 日志查看

### 实时查看应用日志
```bash
# 查看最新日志
tail -f /tmp/xhs_audit.log

# 只看错误
tail -f /tmp/xhs_audit.log | grep ERROR

# 查看浏览器池日志
tail -f /tmp/xhs_audit.log | grep PlaywrightManager

# 查看异步任务日志
tail -f /tmp/xhs_audit.log | grep AsyncAuditService
```

### 查看启动日志
```bash
tail -100 /tmp/xhs_audit.log | grep "Started XhsAuditApplication"
```

预期看到:
```
Started XhsAuditApplication in 6.173 seconds (process running for 6.314)
```

## 🛑 停止应用

### 方式1: Kill进程
```bash
# 查找进程ID
ps aux | grep "mvn spring-boot:run" | grep -v grep

# 停止进程 (替换<PID>为实际进程ID)
kill <PID>
```

### 方式2: 停止所有Java进程 (慎用)
```bash
pkill -f "xhs-audit"
```

### 方式3: 通过端口查找
```bash
lsof -ti:8080 | xargs kill
```

## 🐛 常见问题排查

### 1. 端口8080被占用
```bash
# 查找占用进程
lsof -i :8080

# 停止进程
lsof -ti:8080 | xargs kill
```

### 2. Redis连接失败
```bash
# 检查Redis
redis-cli ping

# 启动Redis
brew services start redis  # macOS
sudo systemctl start redis # Linux

# 验证
redis-cli ping  # 应返回 PONG
```

### 3. PostgreSQL连接失败
```bash
# 检查PostgreSQL
pg_isready

# 测试连接
psql -U postgres -d xhs_audit -c "SELECT 1;"

# 查看连接配置
psql -U postgres -d xhs_audit -c "
  SELECT name, setting 
  FROM pg_settings 
  WHERE name LIKE '%connection%';
"
```

### 4. 应用启动慢
```bash
# 检查浏览器初始化
tail -f /tmp/xhs_audit.log | grep "浏览器实例池初始化完成"

# 检查数据库连接
tail -f /tmp/xhs_audit.log | grep "HikariPool-1"

# 检查Spring启动
tail -f /tmp/xhs_audit.log | grep "Started XhsAuditApplication"
```

### 5. 数据库索引冲突警告
虽然有警告,但不影响运行:
```
WARN  o.h.t.s.i.ExceptionHandlerLoggedImpl - ERROR: relation "idx_job_id" already exists
```

解决方法:
```bash
# 删除冲突索引
psql -U postgres -d xhs_audit <<EOF
DROP INDEX IF EXISTS idx_job_id;
DROP INDEX IF EXISTS idx_post_id;
EOF

# 重启应用
```

## 📊 性能监控

### 查看JVM内存
```bash
jps -l | grep xhs-audit
jmap -heap <PID>
```

### 查看线程状态
```bash
jstack <PID> | grep "xhs-audit-async"
```

### 查看数据库连接池
```bash
psql -U postgres -d xhs_audit -c "
  SELECT count(*) as connections, 
         state 
  FROM pg_stat_activity 
  WHERE datname = 'xhs_audit' 
  GROUP BY state;
"
```

### 查看Redis内存
```bash
redis-cli info memory | grep used_memory_human
```

## 🔧 配置调整

### 修改端口
编辑 `src/main/resources/application.yml`:
```yaml
server:
  port: 8888  # 改为其他端口
```

### 调整线程池
编辑 `src/main/resources/application.yml`:
```yaml
async:
  core-pool-size: 20    # 核心线程数
  max-pool-size: 100    # 最大线程数
  queue-capacity: 2000  # 队列容量
```

### 调整浏览器池
编辑 `PlaywrightManager.java`:
```java
private static final int BROWSER_POOL_SIZE = 5; // 增加到5个
```

## 📝 开发建议

### 1. 添加测试数据
```bash
psql -U postgres -d xhs_audit <<EOF
INSERT INTO audit_rule (dimension, rule_type, rule_name, rule_content, priority, enabled, created_at)
VALUES ('CONTENT', 'KEYWORD', '敏感词检测', '违禁词1,违禁词2', 1, true, NOW());

INSERT INTO sensitive_word (word, level, category, enabled, created_at)
VALUES 
  ('测试敏感词1', 'HIGH', 'ILLEGAL', true, NOW()),
  ('测试敏感词2', 'MEDIUM', 'RISK', true, NOW());
EOF
```

### 2. 创建测试Excel文件
Excel格式 (test-posts.xlsx):
```
| post_id  | url                                      | content      |
|----------|------------------------------------------|--------------|
| test-001 | https://www.xiaohongshu.com/explore/001 | 测试内容1    |
| test-002 | https://www.xiaohongshu.com/explore/002 | 测试内容2    |
```

### 3. 使用Postman测试
导入以下集合 (保存为 `xhs-audit.postman_collection.json`):
```json
{
  "info": {
    "name": "XHS Audit API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Health Check",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/actuator/health"
      }
    },
    {
      "name": "Audit Content",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/v1/audit/content",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"url\": \"https://www.xiaohongshu.com/explore/test\",\n  \"forceRefresh\": false\n}"
        }
      }
    }
  ]
}
```

## 🎯 下一步

1. **功能测试**: 上传真实的小红书链接进行审核
2. **性能测试**: 批量上传1000+链接测试并发能力
3. **集成AI**: 配置OpenAI API Key测试AI审核功能
4. **规则配置**: 添加审核规则和敏感词
5. **前端开发**: 开发管理界面

## 📚 相关文档

- [API测试手册](test-api.md) - 完整的API使用说明
- [README.md](README.md) - 项目概述和架构
- [ROADMAP_2026.md](ROADMAP_2026.md) - 开发路线图

---

**部署完成!** 🎉 现在可以开始使用小红书审核系统了!
