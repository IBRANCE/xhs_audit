# Redis Stream 异步解耦 v4.0 - 测试指南

## 快速开始

### 1. 启动基础设施

```bash
# 启动 Redis + PostgreSQL
docker-compose up -d redis postgres

# 验证服务
docker ps | grep -E "redis|postgres"
```

### 2. 运行数据库迁移

Flyway 会在应用启动时自动执行 V3 迁移，添加 `url` 和 `message` 字段到 `audit_job` 表。

### 3. 配置环境变量

```bash
# 本地开发模式（同时运行 API 和 Workers）
export WORKER_TYPE=both

# 或者生产模式（分离部署）
# 实例 1: export WORKER_TYPE=api
# 实例 2: export WORKER_TYPE=crawler-worker  
# 实例 3: export WORKER_TYPE=audit-worker
```

### 4. 启动应用

```bash
# 方式 1: Maven
./mvnw spring-boot:run

# 方式 2: JAR
java -jar target/xhs-audit-*.jar
```

应用启动时会自动：
- 初始化 Redis Stream 消费者组（`crawl-workers`, `audit-workers`）
- 启动对应的 Worker（基于 `WORKER_TYPE` 配置）

---

## 测试场景

### 场景 1: 单个任务提交

```bash
curl -X POST http://localhost:8080/api/audit/async \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.xiaohongshu.com/explore/67aa7b260000000002022edc"}'
```

**预期响应：**
```json
{
  "jobId": "8f7c3d2a",
  "status": "PENDING",
  "message": "任务已创建，等待爬取"
}
```

### 场景 2: 查询任务状态

```bash
curl http://localhost:8080/api/audit/result/8f7c3d2a
```

**预期状态流转：**
```
PENDING → CRAWLING → CRAWLED → AUDITING → COMPLETED
```

**响应示例：**
```json
{
  "jobId": "8f7c3d2a",
  "status": "COMPLETED",
  "progress": 100,
  "message": "审核完成: LOW",
  "auditResult": { ... },
  "completedAt": "2026-02-03T14:30:00"
}
```

### 场景 3: 批量提交

```bash
curl -X POST http://localhost:8080/api/audit/async/batch \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "https://www.xiaohongshu.com/explore/67aa7b260000000002022edc",
      "https://www.xiaohongshu.com/explore/67aa7b270000000002022edd",
      "https://www.xiaohongshu.com/explore/67aa7b280000000002022ede"
    ]
  }'
```

**预期响应：**
```json
{
  "submitted": 3,
  "jobIds": ["8f7c3d2a", "9a8b4e3c", "7d6c5f1e"]
}
```

### 场景 4: 队列统计

```bash
curl http://localhost:8080/api/audit/queue/stats
```

**预期响应：**
```json
{
  "crawlQueue": {
    "length": 5,
    "pending": 2,
    "consumerGroups": ["crawl-workers"]
  },
  "auditQueue": {
    "length": 3,
    "pending": 1,
    "consumerGroups": ["audit-workers"]
  },
  "deadLetterQueue": {
    "length": 0
  }
}
```

### 场景 5: 死信队列查询

```bash
curl http://localhost:8080/api/audit/dead-letter
```

**预期响应（有失败任务时）：**
```json
[
  {
    "messageId": "1706958600000-0",
    "jobId": "abc123",
    "taskType": "crawl",
    "failedAt": "2026-02-03T15:00:00",
    "errorMessage": "连接超时",
    "retryCount": 3,
    "originalMessage": { ... }
  }
]
```

---

## Redis 监控命令

### 查看 Stream 信息

```bash
# 进入 Redis 容器
docker exec -it <redis_container_id> redis-cli

# 查看爬取队列长度
XLEN xhs:stream:crawl

# 查看审核队列长度  
XLEN xhs:stream:audit

# 查看死信队列长度
XLEN xhs:stream:dead-letter

# 查看消费者组信息
XINFO GROUPS xhs:stream:crawl
XINFO GROUPS xhs:stream:audit

# 查看消费者组的消费者
XINFO CONSUMERS xhs:stream:crawl crawl-workers

# 查看待处理消息（Pending）
XPENDING xhs:stream:crawl crawl-workers
```

### 清空队列（开发调试用）

```bash
# 删除 Stream
DEL xhs:stream:crawl
DEL xhs:stream:audit
DEL xhs:stream:dead-letter

# 重启应用会自动重建消费者组
```

---

## Prometheus 指标

访问 `http://localhost:8080/actuator/prometheus` 查看指标：

### 关键指标

```promql
# 爬取任务成功数
crawl_task_success_total

# 爬取任务失败数
crawl_task_failed_total{reason="TimeoutException"}

# 审核任务成功数（按风险级别分组）
audit_task_success_total{risk_level="LOW"}

# 爬取处理时长
crawl_processing_duration_seconds{status="success",quantile="0.95"}

# 审核处理时长
audit_processing_duration_seconds{status="success",quantile="0.95"}

# 队列长度（需配置 custom metrics）
redis_stream_length{queue="crawl"}
redis_stream_length{queue="audit"}
redis_stream_pending_count{queue="crawl"}
```

---

## 日志监控

### 查看实时日志

```bash
# 所有日志
tail -f logs/xhs-audit.log

# Stream Worker 日志
tail -f logs/stream-consumer.log

# 只看错误
tail -f logs/xhs-audit.log | grep ERROR
```

### 日志过滤示例

```bash
# 查看特定 jobId 的处理流程
grep "jobId=8f7c3d2a" logs/xhs-audit.log

# 查看失败任务
grep "任务失败" logs/stream-consumer.log | jq .

# 查看重试任务
grep "RETRYING" logs/stream-consumer.log
```

---

## 性能测试

### 基准测试（100 个任务）

```bash
# 生成测试 URLs
cat <<EOF > urls.txt
https://www.xiaohongshu.com/explore/67aa7b260000000002022edc
https://www.xiaohongshu.com/explore/67aa7b270000000002022edd
# ... 添加 100 个 URL
EOF

# 提交测试（使用 parallel）
cat urls.txt | parallel -j 10 'curl -X POST http://localhost:8080/api/audit/async \
  -H "Content-Type: application/json" -d "{\"url\": \"{}\"}" -s'

# 或使用 Apache Bench
ab -n 100 -c 10 -T 'application/json' \
  -p <(echo '{"url":"https://www.xiaohongshu.com/explore/67aa7b260000000002022edc"}') \
  http://localhost:8080/api/audit/async
```

### 预期性能指标

基于 v4.0 设计目标：

- **同步模式（v3.0）：** 1000 条任务 ~25 分钟
- **异步模式（v4.0）：** 1000 条任务 ~8-10 分钟 ⚡️
- **吞吐量提升：** ~2.5-3倍

**测量方法：**
```bash
# 记录开始时间
start=$(date +%s)

# 提交 1000 个任务...

# 等待所有任务完成（轮询）
while [ $(curl -s http://localhost:8080/api/audit/queue/stats | jq '.crawlQueue.length + .auditQueue.length') -gt 0 ]; do
  sleep 5
done

# 记录结束时间
end=$(date +%s)
echo "总耗时: $((end - start)) 秒"
```

---

## 故障模拟

### 1. Worker 崩溃恢复

```bash
# 杀死 Worker 进程
kill -9 <worker_pid>

# 等待 60 秒（pending-retry-interval-ms）
# 观察日志：@Scheduled 任务会自动 reclaim 未确认消息
tail -f logs/stream-consumer.log | grep "Reclaim"
```

### 2. Redis 连接断开

```bash
# 停止 Redis
docker stop <redis_container_id>

# 观察应用日志：应该有重试日志
tail -f logs/xhs-audit.log | grep "Redis"

# 重启 Redis
docker start <redis_container_id>

# 应用应自动重连
```

### 3. LLM 服务不可用

```bash
# 修改配置使 LLM 调用失败
export SPRING_AI_OPENAI_API_KEY=invalid_key

# 提交任务
curl -X POST http://localhost:8080/api/audit/async \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.xiaohongshu.com/explore/67aa7b260000000002022edc"}'

# 观察重试和断路器行为
tail -f logs/xhs-audit.log | grep -E "Retry|CircuitBreaker|降级"
```

**预期行为：**
- 第 1-3 次：重试（Resilience4j Retry）
- 第 4 次开始：断路器打开（CircuitBreaker）
- 返回结果：`status=UNCERTAIN`, `message="LLM服务暂时不可用"`

---

## 多实例测试（生产模拟）

### 终端 1: API Server

```bash
export WORKER_TYPE=api
java -jar target/xhs-audit-*.jar --server.port=8080
```

### 终端 2: Crawler Worker

```bash
export WORKER_TYPE=crawler-worker
export AUDIT_WORKER_ENABLED=true
java -jar target/xhs-audit-*.jar --server.port=8081
```

### 终端 3: Audit Worker（实例 1）

```bash
export WORKER_TYPE=audit-worker
export AUDIT_WORKER_ENABLED=true
java -jar target/xhs-audit-*.jar --server.port=8082
```

### 终端 4: Audit Worker（实例 2）

```bash
export WORKER_TYPE=audit-worker
export AUDIT_WORKER_ENABLED=true
java -jar target/xhs-audit-*.jar --server.port=8083
```

**验证负载均衡：**
```bash
# 提交 20 个任务
for i in {1..20}; do
  curl -X POST http://localhost:8080/api/audit/async \
    -H "Content-Type: application/json" \
    -d '{"url":"https://www.xiaohongshu.com/explore/67aa7b260000000002022edc"}' &
done

# 查看各实例消费的消息数
grep "开始处理" logs/stream-consumer.log | awk -F'consumer=' '{print $2}' | cut -d',' -f1 | sort | uniq -c
```

**预期输出：**
```
  10 local-audit-abc123
  10 local-audit-def456
```

---

## 常见问题排查

### 问题 1: 消息一直处于 PENDING 状态

**排查步骤：**
```bash
# 1. 检查 Worker 是否启动
ps aux | grep java

# 2. 检查消费者组是否存在
redis-cli XINFO GROUPS xhs:stream:crawl

# 3. 检查 Pending 消息详情
redis-cli XPENDING xhs:stream:crawl crawl-workers - + 10
```

**解决方案：**
- 确认 `audit.worker.enabled=true`
- 确认 `WORKER_TYPE` 正确配置
- 手动 reclaim: `XCLAIM xhs:stream:crawl crawl-workers <consumer-name> <idle-time> <message-id>`

### 问题 2: 大量任务进入死信队列

**排查步骤：**
```bash
# 查看死信队列内容
redis-cli XRANGE xhs:stream:dead-letter - +

# 查看错误原因
curl http://localhost:8080/api/audit/dead-letter | jq '.[] | {jobId, errorMessage, retryCount}'
```

**常见原因：**
- Selenium 无法启动（检查 WebDriver 配置）
- LLM API 配额用尽（检查 API Key）
- 数据库连接池耗尽（调整 Hikari 配置）

### 问题 3: 内存溢出（OOM）

**排查步骤：**
```bash
# 查看 JVM 内存使用
jcmd <pid> VM.native_memory summary

# 查看线程数
jstack <pid> | grep -c "java.lang.Thread.State"

# 查看数据库连接池
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

**解决方案：**
- 调整 `-Xmx` 参数（推荐 4GB+）
- 降低 `spring.data.redis.stream.batch-size`（默认 10）
- 减少 `audit.executor.stream-consumer-threads`

---

## 成功标准

✅ 所有测试场景通过  
✅ 1000 条任务在 10 分钟内完成  
✅ 无消息丢失（pending 消息被正确 reclaim）  
✅ 断路器正常工作（LLM 故障时降级）  
✅ Prometheus 指标正常上报  
✅ 日志结构化且可查询  
✅ 多实例负载均衡

---

## 下一步

- [ ] 部署到 Kubernetes（使用 HPA 自动扩容）
- [ ] 接入 Grafana 可视化监控
- [ ] 配置告警规则（死信队列长度 > 10）
- [ ] 实现任务优先级（VIP 用户优先处理）
- [ ] 添加 API 限流（防止 DDoS）

---

**文档版本:** v4.0  
**最后更新:** 2026-02-03  
**作者:** XHS Audit System
