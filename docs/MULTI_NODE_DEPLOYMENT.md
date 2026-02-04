# 多节点部署最佳实践

## 概述

本文档详细说明在多节点环境下部署爬虫（Crawler）和审核（Audit）Worker 时如何解决**竞态竞争（Race Condition）**和**重复消费（Duplicate Consumption）**问题。

---

## 核心挑战

### 1. 竞态竞争 (Race Condition)

**问题描述：**
- 多个 Worker 同时处理同一任务，导致状态冲突
- 爬虫频率失控，触发目标网站封禁
- 数据库写入冲突，状态机异常

**解决方案：**

#### 1.1 分布式锁（Redisson）

```java
RLock lock = redissonClient.getLock(LOCK_PREFIX_CRAWLER + jobId);
boolean locked = lock.tryLock(30, 120, TimeUnit.SECONDS);

if (!locked) {
    log.warn("[CrawlerWorker] 获取分布式锁失败: jobId={}", jobId);
    // 不 ACK，让消息保持在 Pending 状态
    return;
}
```

**配置：**
```yaml
# application.yml
spring:
  redis:
    redisson:
      config: |
        singleServerConfig:
          address: "redis://localhost:6379"
          timeout: 3000
          connectionPoolSize: 64
          connectionMinimumIdleSize: 10
```

#### 1.2 双重检查锁（Double-Checked Locking）

```java
// 第一次检查：快速过滤（无锁）
var jobOpt = auditJobRepository.findByJobId(jobId);
if (jobOpt.isPresent() && isCompleted(jobOpt.get())) {
    log.info("任务已完成，跳过");
    messageQueueService.acknowledgeCrawlTask(messageId);
    return;
}

// 获取分布式锁
RLock lock = redissonClient.getLock(LOCK_PREFIX + jobId);
lock.tryLock(30, 120, TimeUnit.SECONDS);

try {
    // 第二次检查：锁内确认
    jobOpt = auditJobRepository.findByJobId(jobId);
    if (jobOpt.isPresent() && isCompleted(jobOpt.get())) {
        log.info("锁内检查：任务已完成，跳过");
        return;
    }
    
    // 现在才安全地更新状态并处理任务
    updateJobStatus(jobId, TaskStatus.CRAWLING);
    processCrawlTask(...);
} finally {
    lock.unlock();
}
```

**优势：**
- 减少不必要的锁竞争（大部分任务在第一次检查时就被过滤）
- 保证锁内状态一致性
- 性能优化：只有真正需要处理的任务才获取锁

#### 1.3 乐观锁（Status Guard）

```java
private boolean shouldUpdateStatus(String currentStatus, String newStatus) {
    // 防止状态回退
    if (TaskStatus.COMPLETED.name().equals(currentStatus) ||
        TaskStatus.CRAWLED.name().equals(currentStatus) ||
        TaskStatus.FAILED.name().equals(currentStatus)) {
        return false;
    }
    return true;
}

private void updateJobStatus(String jobId, String status, String message) {
    auditJobRepository.findByJobId(jobId).ifPresent(job -> {
        String currentStatus = job.getStatus();
        if (shouldUpdateStatus(currentStatus, status)) {
            job.setStatus(status);
            job.setMessage(message);
            job.setUpdatedAt(LocalDateTime.now());
            auditJobRepository.save(job);
            log.debug("状态更新: {} -> {}", currentStatus, status);
        } else {
            log.warn("状态更新被阻止: {} -/-> {}", currentStatus, status);
        }
    });
}
```

---

### 2. 重复消费 (Duplicate Consumption)

**问题描述：**
- 同一消息被多次递送（网络波动、Worker 崩溃）
- 重复爬取浪费资源
- 重复审核导致数据冗余

**解决方案：**

#### 2.1 幂等性设计（数据库唯一索引）

**方案 A：任务级幂等（推荐）**

```sql
-- V4__add_unique_constraints.sql
ALTER TABLE audit_job 
ADD CONSTRAINT uk_job_id UNIQUE (job_id);

ALTER TABLE xhs_content 
ADD CONSTRAINT uk_post_id UNIQUE (post_id);

-- 防止同一 URL 重复爬取（可选）
ALTER TABLE xhs_content 
ADD CONSTRAINT uk_url UNIQUE (url);
```

**方案 B：业务级幂等**

```java
// 尝试保存内容，如果已存在则更新
public XhsContent saveOrUpdateContent(XhsContent content) {
    return xhsContentRepository.findByPostId(content.getPostId())
        .map(existing -> {
            log.info("内容已存在，更新: postId={}", content.getPostId());
            existing.setTitle(content.getTitle());
            existing.setContent(content.getContent());
            existing.setUpdatedAt(LocalDateTime.now());
            return xhsContentRepository.save(existing);
        })
        .orElseGet(() -> {
            log.info("保存新内容: postId={}", content.getPostId());
            return xhsContentRepository.save(content);
        });
}
```

#### 2.2 消息确认机制

```java
// 只在业务逻辑完成后才 ACK
try {
    // 执行爬虫
    XhsContent content = crawlerService.crawlContent(url);
    
    // 保存到数据库
    xhsContentRepository.save(content);
    
    // 发送审核任务
    messageQueueService.sendAuditTask(auditMessage);
    
    // 最后才确认消息
    messageQueueService.acknowledgeCrawlTask(messageId);
    
} catch (Exception e) {
    // 失败不 ACK，消息会重新投递
    log.error("处理失败，消息将重新投递", e);
}
```

**Redis Stream 配置：**

```yaml
spring:
  data:
    redis:
      stream:
        consumer-group: xhs-audit-workers
        block-time: 5000  # 阻塞等待时间
        batch-size: 10    # 批量消费大小
        max-retry-attempts: 3  # 最大重试次数
```

#### 2.3 布隆过滤器（可选优化）

```java
@Component
public class CrawlDuplicateFilter {
    
    @Autowired
    private RedissonClient redissonClient;
    
    public boolean shouldCrawl(String url) {
        RBloomFilter<String> filter = redissonClient
            .getBloomFilter("crawl:url:filter");
        
        // 检查是否已爬取
        if (filter.contains(url)) {
            log.info("URL 已爬取，跳过: {}", url);
            return false;
        }
        
        // 标记为已爬取
        filter.add(url);
        return true;
    }
}

// 初始化布隆过滤器
@PostConstruct
public void initBloomFilter() {
    RBloomFilter<String> filter = redissonClient
        .getBloomFilter("crawl:url:filter");
    filter.tryInit(100000L, 0.01); // 预期 10 万条，1% 误判率
}
```

---

## 实现清单

### ✅ 已实现

- [x] **分布式锁**：基于 Redisson 的任务级锁
- [x] **双重检查锁**：无锁快速过滤 + 锁内二次确认
- [x] **状态回退保护**：乐观锁防止状态机异常
- [x] **消息确认机制**：业务完成后才 ACK
- [x] **重试策略**：最大 3 次重试，超过移入死信队列
- [x] **指标监控**：成功/失败/跳过计数器
- [x] **优雅停机**：等待当前任务完成

### 🔲 待实现

- [ ] **数据库唯一索引**：防止重复写入（SQL 迁移脚本）
- [ ] **布隆过滤器**：URL 去重（可选，性能优化）
- [ ] **限流器**：全局爬虫频率控制
- [ ] **断路器**：AI 审核服务保护（已在 Agent 层实现）

---

## 部署配置

### 单节点模式（开发环境）

```yaml
# application-local.yml
audit:
  worker:
    enabled: true
    type: both  # 同时运行爬虫和审核 Worker
    consumer-name-prefix: local
```

### 多节点模式（生产环境）

**Node 1 & 2: 爬虫 Worker**
```yaml
# application-crawler.yml
audit:
  worker:
    enabled: true
    type: crawler-worker
    consumer-name-prefix: crawler-node-1  # 每个节点不同
```

**Node 3 & 4: 审核 Worker**
```yaml
# application-audit.yml
audit:
  worker:
    enabled: true
    type: audit-worker
    consumer-name-prefix: audit-node-1  # 每个节点不同
```

### Docker Compose 部署

```yaml
version: '3.8'
services:
  crawler-worker-1:
    image: xhs-audit:latest
    environment:
      SPRING_PROFILES_ACTIVE: crawler
      AUDIT_WORKER_CONSUMER_NAME_PREFIX: crawler-node-1
    depends_on:
      - redis
      - postgres
  
  crawler-worker-2:
    image: xhs-audit:latest
    environment:
      SPRING_PROFILES_ACTIVE: crawler
      AUDIT_WORKER_CONSUMER_NAME_PREFIX: crawler-node-2
    depends_on:
      - redis
      - postgres
  
  audit-worker-1:
    image: xhs-audit:latest
    environment:
      SPRING_PROFILES_ACTIVE: audit
      AUDIT_WORKER_CONSUMER_NAME_PREFIX: audit-node-1
    depends_on:
      - redis
      - postgres
  
  audit-worker-2:
    image: xhs-audit:latest
    environment:
      SPRING_PROFILES_ACTIVE: audit
      AUDIT_WORKER_CONSUMER_NAME_PREFIX: audit-node-2
    depends_on:
      - redis
      - postgres
```

---

## 监控指标

### Prometheus 指标

```java
// 任务处理指标
crawl.task.success
crawl.task.failed{reason="TimeoutException"}
crawl.task.skipped{reason="already_completed"}
crawl.task.skipped{reason="double_check_completed"}
crawl.processing.duration{status="success"}

audit.task.success{risk_level="HIGH"}
audit.task.failed{reason="AIServiceException"}
audit.task.skipped{reason="already_completed"}
audit.processing.duration{status="success"}
```

### Grafana 仪表盘

```promql
# 每秒处理速率
rate(crawl_task_success_total[1m])

# 失败率
rate(crawl_task_failed_total[1m]) / 
rate(crawl_task_success_total[1m] + crawl_task_failed_total[1m])

# 平均处理时长
rate(crawl_processing_duration_sum[1m]) / 
rate(crawl_processing_duration_count[1m])

# 重复消费率
rate(crawl_task_skipped_total{reason="double_check_completed"}[5m])
```

---

## 故障排查

### 问题 1：任务重复处理

**症状：**
- 日志中出现大量 "锁内检查：任务已完成，跳过"
- `crawl.task.skipped{reason="double_check_completed"}` 指标上升

**原因：**
- 消息未正确 ACK，导致重新投递
- 多个 Worker 同时抢占任务

**解决：**
1. 检查分布式锁配置
2. 检查消息 ACK 逻辑
3. 增加唯一索引约束

### 问题 2：任务积压

**症状：**
- Redis Stream Pending 消息数量持续上升
- Worker 日志中大量 "获取分布式锁失败"

**原因：**
- 锁等待时间过长
- Worker 数量不足
- 某个任务卡死占用锁

**解决：**
1. 调整锁超时时间
2. 增加 Worker 节点
3. 添加锁释放监控
4. 实现任务超时机制

### 问题 3：状态不一致

**症状：**
- 任务状态从 COMPLETED 回退到 CRAWLING
- 日志中出现 "状态更新被阻止"

**原因：**
- 并发更新冲突
- 消息乱序处理

**解决：**
1. 检查 `shouldUpdateStatus` 逻辑
2. 确认数据库事务隔离级别
3. 添加乐观锁版本号

---

## 性能优化

### 1. 批量消费

```yaml
spring:
  data:
    redis:
      stream:
        batch-size: 50  # 增加批量大小
```

### 2. 锁粒度优化

```java
// 使用更细粒度的锁
String lockKey = LOCK_PREFIX + jobId + ":" + operationType;
```

### 3. 异步处理

```java
@Async("crawlTaskExecutor")
public CompletableFuture<XhsContent> crawlContentAsync(String url) {
    return CompletableFuture.completedFuture(crawlContent(url));
}
```

---

## 总结

通过以下机制保证多节点部署的正确性：

| 问题 | 解决方案 | 实现状态 |
|------|---------|---------|
| 竞态竞争 | 分布式锁 + 双重检查锁 | ✅ 已实现 |
| 状态回退 | 乐观锁状态守卫 | ✅ 已实现 |
| 重复消费 | 幂等性设计 + 唯一索引 | 🔲 部分实现 |
| 消息丢失 | 手动 ACK + 重试机制 | ✅ 已实现 |
| 性能瓶颈 | 批量消费 + 指标监控 | ✅ 已实现 |

**核心原则：**
1. **先检查后加锁**（双重检查锁）
2. **先处理后确认**（消息 ACK）
3. **只进不退**（状态机保护）
4. **唯一标识**（数据库约束）
5. **可观测性**（指标监控）

---

## 参考资料

- [Redis Distributed Lock](https://redis.io/topics/distlock)
- [Double-Checked Locking Pattern](https://en.wikipedia.org/wiki/Double-checked_locking)
- [Idempotency in Distributed Systems](https://martinfowler.com/articles/patterns-of-distributed-systems/idempotent-receiver.html)
- [Redis Streams](https://redis.io/topics/streams-intro)
