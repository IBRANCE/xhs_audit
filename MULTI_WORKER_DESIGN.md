# 多Worker并行爬取架构设计

## 问题分析

### 当前性能瓶颈
- **单线程爬取**: 1000条 × 8秒 = 2.2小时
- **Playwright限制**: 多线程会导致 "connection closed" 崩溃
- **无法横向扩展**: 数据量增长时性能线性下降

### 根本原因
Playwright Java SDK 基于单个 Node.js 子进程，WebSocket 通道不支持多线程并发。

---

## 推荐方案：基于 Redis 队列的多进程 Worker 架构

### 架构图
```
┌─────────────────────────────────────────────────────────┐
│                   Master 进程                            │
│  Spring Boot 应用（当前代码）                           │
│  - 接收用户请求                                         │
│  - Excel 解析                                            │
│  - 任务分发到 Redis 队列                                │
│  - 进度监控与结果汇总                                   │
└─────────────────────────────────────────────────────────┘
                          ↓ (Redis)
        ┌─────────────────────────────────────┐
        │   Redis 任务队列 (xhs:crawl:queue)  │
        │   URL#1, URL#2, ..., URL#1000       │
        └─────────────────────────────────────┘
          ↓           ↓           ↓           ↓
    ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
    │Worker-1 │ │Worker-2 │ │Worker-3 │ │Worker-4 │
    │独立进程 │ │独立进程 │ │独立进程 │ │独立进程 │
    │PW实例-1 │ │PW实例-2 │ │PW实例-3 │ │PW实例-4 │
    └─────────┘ └─────────┘ └─────────┘ └─────────┘
          ↓           ↓           ↓           ↓
        └───────────────────────────────────────┘
                          ↓
              结果存储到 PostgreSQL
```

### 性能提升
- **4个Worker**: 1000条 ÷ 4 = 250条/Worker × 8秒 = 2000秒 ≈ **33分钟**
- **8个Worker**: 1000条 ÷ 8 = 125条/Worker × 8秒 = 1000秒 ≈ **17分钟**
- **理论极限**: 受限于机器CPU/内存，建议单机 4-8 Worker

---

## 实现步骤

### Phase 1: 改造 Master 进程（当前应用）

#### 1.1 添加 Redis 任务队列依赖
```xml
<!-- pom.xml 已有 Spring Data Redis -->
```

#### 1.2 修改 AsyncAuditService
```java
@Service
public class AsyncAuditService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Async("taskExecutor")
    public void processAuditJob(String jobId, List<String> urls) {
        // 1. 将URL推送到Redis队列
        String queueKey = "xhs:crawl:queue:" + jobId;
        for (String url : urls) {
            redisTemplate.opsForList().rightPush(queueKey, url);
        }
        
        // 2. 更新任务状态
        updateJobStatus(jobId, "PROCESSING");
        
        // 3. 等待Workers完成（监听结果队列）
        waitForCompletion(jobId, urls.size());
    }
}
```

### Phase 2: 创建独立的 Worker 进程

#### 2.1 Worker 主程序
```java
// src/main/java/com/xhs/audit/worker/CrawlWorker.java
@SpringBootApplication
public class CrawlWorker {
    
    public static void main(String[] args) {
        SpringApplication.run(CrawlWorker.class, args);
    }
    
    @Bean
    public CommandLineRunner startWorker(
            RedisTemplate<String, String> redis,
            CrawlerService crawlerService,
            ContentAuditService auditService) {
        
        return args -> {
            String workerId = InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
            log.info("Worker启动: {}", workerId);
            
            while (!Thread.currentThread().isInterrupted()) {
                // 1. 从Redis队列获取URL (阻塞式，5秒超时)
                String url = redis.opsForList().leftPop(
                    "xhs:crawl:queue:*", 5, TimeUnit.SECONDS
                );
                
                if (url == null) continue;
                
                try {
                    // 2. 爬取 + 审核
                    XhsContent content = crawlerService.crawlContent(url);
                    AuditDecision decision = auditService.auditContent(content, null);
                    
                    // 3. 写入结果
                    redis.opsForList().rightPush(
                        "xhs:crawl:result:" + jobId, 
                        toJson(decision)
                    );
                    
                    log.info("Worker[{}] 完成: {}", workerId, url);
                    
                } catch (Exception e) {
                    log.error("Worker[{}] 失败: {}", workerId, url, e);
                    redis.opsForList().rightPush(
                        "xhs:crawl:failed:" + jobId, url
                    );
                }
            }
        };
    }
}
```

#### 2.2 启动多个Worker进程
```bash
#!/bin/bash
# scripts/start-workers.sh

# 启动4个Worker进程
for i in {1..4}; do
    nohup java -jar xhs-audit-worker.jar \
        --server.port=0 \
        --worker.id=$i \
        > logs/worker-$i.log 2>&1 &
    echo "Started Worker-$i (PID: $!)"
done
```

### Phase 3: 监控与管理

#### 3.1 进度监控
```java
private void waitForCompletion(String jobId, int totalCount) {
    String resultKey = "xhs:crawl:result:" + jobId;
    String failedKey = "xhs:crawl:failed:" + jobId;
    
    while (true) {
        long completed = redis.opsForList().size(resultKey);
        long failed = redis.opsForList().size(failedKey);
        
        if (completed + failed >= totalCount) {
            break;
        }
        
        // 更新进度
        updateJobProgress(jobId, (int)completed, (int)failed);
        Thread.sleep(2000);
    }
}
```

---

## 方案对比

### 方案1: Redis 队列 + 多进程 Worker（推荐）
✅ **优点**:
- 完全隔离的 Playwright 实例，避免冲突
- 横向扩展，可动态增减 Worker
- 容错性好，单个 Worker 挂了不影响其他
- 支持分布式部署（多台机器）

⚠️ **缺点**:
- 需要 Redis 依赖
- 需要管理多个进程
- 增加部署复杂度

**适用场景**: 生产环境，需要高性能和可扩展性

---

### 方案2: 切换到支持多线程的爬虫库
使用 Selenium Grid 或 Puppeteer Cluster：

```java
// 使用 Selenium Grid（支持真正的多线程）
@Bean
public RemoteWebDriver createDriver() {
    return new RemoteWebDriver(
        new URL("http://selenium-grid:4444"),
        new ChromeOptions()
    );
}
```

✅ **优点**: 架构简单，直接多线程并发
⚠️ **缺点**: 需要替换 Playwright，代码改动大

---

### 方案3: 优化单线程性能
如果短期内无法实现多Worker，可以先优化单线程：

```java
// 1. 跳过已爬取的URL（缓存命中率提升）
if (contentRepository.existsByUrl(url)) {
    return contentRepository.findByUrl(url).get();
}

// 2. 降低爬取延迟
page.waitForLoadState(LoadState.DOMCONTENTLOADED); // 替代完整加载

// 3. 禁用不必要的资源加载
context.route("**/*.{png,jpg,jpeg,gif,svg,css,font}", route -> route.abort());
```

预期提升: 8秒/条 → 5秒/条，1000条 ≈ **1.4小时**

---

## 推荐实施路线

### 短期（本周）- 优化单线程
1. 实现缓存跳过逻辑
2. 优化页面加载策略
3. 目标: 处理时间降至 1.5 小时

### 中期（2周内）- 多进程Worker
1. 实现 Redis 队列架构
2. 创建 Worker 启动脚本
3. 本地测试 4 Worker 并发
4. 目标: 处理时间降至 30 分钟

### 长期（1个月）- 分布式部署
1. Docker 容器化 Worker
2. Kubernetes 自动扩缩容
3. 目标: 支持万级数据处理

---

## 立即可做的快速优化

不改架构，先提升 30% 性能：
