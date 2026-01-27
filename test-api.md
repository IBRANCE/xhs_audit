# 小红书审核系统 - API 测试手册

## 应用信息
- **应用名称**: 小红书审核系统 (XHS Audit System)
- **版本**: 1.0.0-SNAPSHOT
- **端口**: 8080
- **基础URL**: http://localhost:8080

## 系统状态

### 1. 健康检查
```bash
curl http://localhost:8080/actuator/health | jq '.'
```

**预期响应**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### 2. 应用信息
```bash
curl http://localhost:8080/actuator/info | jq '.'
```

## 核心功能测试

### 1. Excel文件上传审核

#### 1.1 准备测试文件
创建一个包含审核数据的Excel文件 `test-posts.xlsx`,包含以下列:
- post_id: 小红书帖子ID
- content: 帖子内容
- author: 作者
- publish_time: 发布时间

示例数据:
```
post_id          | content                    | author  | publish_time
----------------|----------------------------|---------|---------------
test-001        | 这是一条测试内容            | 张三    | 2024-01-01
test-002        | 今天天气真好！               | 李四    | 2024-01-02
```

#### 1.2 上传文件
```bash
curl -X POST http://localhost:8080/api/v1/audit/upload \
  -F "file=@test-posts.xlsx" \
  -F "taskName=测试任务-$(date +%Y%m%d-%H%M%S)" \
  -v
```

**预期响应**:
```json
{
  "code": 200,
  "message": "审核任务创建成功",
  "data": {
    "jobId": "uuid",
    "taskName": "测试任务-...",
    "totalPosts": 2,
    "status": "PENDING"
  }
}
```

### 2. 查询审核任务状态

#### 2.1 查询所有任务
```bash
curl http://localhost:8080/api/v1/audit/jobs | jq '.'
```

#### 2.2 查询单个任务
```bash
JOB_ID="your-job-id"
curl "http://localhost:8080/api/v1/audit/jobs/${JOB_ID}" | jq '.'
```

**预期响应**:
```json
{
  "code": 200,
  "data": {
    "jobId": "uuid",
    "taskName": "测试任务",
    "status": "COMPLETED",
    "totalPosts": 2,
    "processedPosts": 2,
    "violationCount": 0,
    "progress": 100.0,
    "startTime": "2024-01-01T10:00:00",
    "endTime": "2024-01-01T10:05:00"
  }
}
```

### 3. 查询审核结果

#### 3.1 按任务ID查询
```bash
JOB_ID="your-job-id"
curl "http://localhost:8080/api/v1/audit/results?jobId=${JOB_ID}" | jq '.'
```

#### 3.2 只查询违规内容
```bash
JOB_ID="your-job-id"
curl "http://localhost:8080/api/v1/audit/results?jobId=${JOB_ID}&violationOnly=true" | jq '.'
```

**预期响应**:
```json
{
  "code": 200,
  "data": {
    "results": [
      {
        "postId": "test-001",
        "content": "违规内容...",
        "auditResult": "VIOLATION",
        "violationType": "SENSITIVE_WORD",
        "reason": "包含敏感词: xxx",
        "confidence": 0.95
      }
    ],
    "total": 1
  }
}
```

### 4. 下载审核结果

```bash
JOB_ID="your-job-id"
curl -O -J "http://localhost:8080/api/v1/audit/download/${JOB_ID}"
```

这将下载一个Excel文件,包含所有审核结果。

## 审核规则配置

### 1. 查询审核规则
```bash
curl http://localhost:8080/api/v1/rules | jq '.'
```

### 2. 添加审核规则
```bash
curl -X POST http://localhost:8080/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{
    "dimension": "CONTENT",
    "ruleType": "KEYWORD",
    "ruleName": "敏感词检测",
    "ruleContent": "违禁词1,违禁词2",
    "severity": "HIGH",
    "action": "BLOCK",
    "priority": 1,
    "enabled": true
  }' | jq '.'
```

### 3. 更新审核规则
```bash
RULE_ID="rule-id"
curl -X PUT "http://localhost:8080/api/v1/rules/${RULE_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": false
  }' | jq '.'
```

### 4. 删除审核规则
```bash
RULE_ID="rule-id"
curl -X DELETE "http://localhost:8080/api/v1/rules/${RULE_ID}"
```

## 敏感词管理

### 1. 查询敏感词列表
```bash
curl http://localhost:8080/api/v1/sensitive-words | jq '.'
```

### 2. 批量添加敏感词
```bash
curl -X POST http://localhost:8080/api/v1/sensitive-words/batch \
  -H "Content-Type: application/json" \
  -d '{
    "words": ["敏感词1", "敏感词2", "敏感词3"],
    "level": "HIGH",
    "category": "ILLEGAL"
  }' | jq '.'
```

## 数据库查询

### 1. 查看审核任务
```bash
psql -U postgres -d xhs_audit -c "SELECT * FROM audit_job ORDER BY created_at DESC LIMIT 5;"
```

### 2. 查看审核结果
```bash
psql -U postgres -d xhs_audit -c "SELECT * FROM audit_result ORDER BY created_at DESC LIMIT 10;"
```

### 3. 查看审核规则
```bash
psql -U postgres -d xhs_audit -c "SELECT * FROM audit_rule WHERE enabled = true;"
```

### 4. 查看敏感词
```bash
psql -U postgres -d xhs_audit -c "SELECT * FROM sensitive_word ORDER BY level DESC;"
```

## 性能测试

### 1. 并发上传测试
```bash
# 同时上传10个文件
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/audit/upload \
    -F "file=@test-posts.xlsx" \
    -F "taskName=并发测试-$i" &
done
wait
```

### 2. 查看线程池状态
```bash
# 检查应用日志中的线程池信息
tail -f /tmp/xhs_audit.log | grep "异步任务线程池"
```

## 常见问题排查

### 1. 应用无响应
```bash
# 检查应用是否运行
ps aux | grep xhs_audit

# 检查端口占用
lsof -i :8080

# 查看最新日志
tail -100 /tmp/xhs_audit.log
```

### 2. 数据库连接失败
```bash
# 检查PostgreSQL状态
psql -U postgres -c "SELECT version();"

# 检查数据库连接
psql -U postgres -d xhs_audit -c "\dt"
```

### 3. Redis连接失败
```bash
# 检查Redis状态
redis-cli ping

# 启动Redis
brew services start redis
```

### 4. 浏览器实例池异常
```bash
# 检查Playwright浏览器
ps aux | grep chromium

# 查看浏览器日志
tail -f /tmp/xhs_audit.log | grep PlaywrightManager
```

## 测试数据清理

### 清空测试数据
```bash
psql -U postgres -d xhs_audit <<EOF
TRUNCATE TABLE audit_result CASCADE;
TRUNCATE TABLE xhs_content CASCADE;
TRUNCATE TABLE audit_job CASCADE;
-- 保留审核规则和敏感词表
EOF
```

## API响应码说明

- **200**: 请求成功
- **400**: 请求参数错误
- **404**: 资源不存在
- **500**: 服务器内部错误
- **503**: 服务不可用(浏览器池耗尽等)

## 应用停止

```bash
# 停止后台进程
ps aux | grep "mvn spring-boot:run" | grep -v grep | awk '{print $2}' | xargs kill

# 或者使用进程ID
kill 48302
```

## 下一步测试建议

1. **功能测试**: 
   - 上传不同格式的Excel文件
   - 测试各种违规内容检测
   - 验证审核规则优先级

2. **性能测试**:
   - 大文件上传(1000+行)
   - 并发请求压力测试
   - 浏览器池资源管理

3. **集成测试**:
   - 完整的审核流程
   - 数据导出完整性
   - AI审核效果评估
