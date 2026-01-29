# 日志记录指南

本文档说明系统中各阶段的日志记录规范，确保流程图中的每个步骤都能在日志中清晰追踪。

## 日志级别说明

- **INFO**: 关键流程节点、状态变更、重要业务操作
- **DEBUG**: 详细的技术细节、缓存命中情况、中间状态
- **WARN**: 警告信息、性能问题、重试操作
- **ERROR**: 错误信息、异常堆栈

## 流程日志结构

### 1. 同步处理阶段（用户上传 → 返回jobId）

#### ExcelAuditService

```
[同步阶段] 开始处理Excel上传: filename=xxx.xlsx, size=12345
[同步阶段] Apache POI解析Excel，提取小红书链接...
[同步阶段] Excel解析完成: 提取到50条链接
[同步阶段] 链接验证与去重: 原始50条, 去重后48条
[同步阶段] 创建审核任务: jobId=AUDIT_20260128_001, status=PENDING, totalLinks=48
[同步阶段] 提交后台异步任务: jobId=AUDIT_20260128_001
[同步阶段完成] 返回jobId给用户: AUDIT_20260128_001, 用户无需等待，可通过jobId查询进度
```

**关键日志点**：
- Excel文件接收和验证
- 链接提取数量
- 去重前后对比
- 任务创建状态
- 异步任务提交确认

---

### 2. 异步处理阶段（后台线程池执行）

#### AsyncAuditService

```
========================================
[异步阶段] 开始后台处理: jobId=AUDIT_20260128_001, totalLinks=48
========================================
[异步阶段] 更新任务状态: jobId=AUDIT_20260128_001, PENDING -> PROCESSING
[异步阶段] 开始批处理循环: 共48条链接待处理
[异步阶段] 处理第1/48条链接: https://www.xiaohongshu.com/explore/abc123
[异步阶段] 处理第2/48条链接: https://www.xiaohongshu.com/discovery/item/def456
...
[异步阶段] 更新任务进度: jobId=AUDIT_20260128_001, 已完成=10/48, 成功=8, 失败=2
[异步阶段] 更新任务进度: jobId=AUDIT_20260128_001, 已完成=20/48, 成功=17, 失败=3
...
[异步阶段] 批处理完成，更新最终状态: jobId=AUDIT_20260128_001, PROCESSING -> COMPLETED
========================================
[异步阶段完成] jobId=AUDIT_20260128_001, status=COMPLETED, 总计=48, 成功=45, 失败=3
========================================
```

**关键日志点**：
- 异步任务启动
- 状态更新（PENDING → PROCESSING）
- 批处理进度（每处理1条记录1次，每10条更新数据库）
- 成功/失败统计
- 最终状态更新（COMPLETED/PARTIAL_SUCCESS）

---

### 3. 审核流程阶段（单条链接处理）

#### ContentAuditService

```
[审核流程] 开始: url=https://www.xiaohongshu.com/explore/abc123, forceRefresh=false
[审核流程] 提取PostID: abc123
[审核流程] 检查审核结果缓存: postId=abc123
[审核流程] 未命中审核结果缓存，继续处理
[审核流程] 调用爬虫服务获取内容: postId=abc123
[审核流程] 内容获取成功: postId=abc123, title=这是一个测试标题
[审核流程] 调用ContentAuditAgent进行AI审核: postId=abc123
[审核流程] Agent审核完成: postId=abc123, status=PASSED, confidence=0.95
[审核流程] 保存审核结果到数据库: postId=abc123
[审核流程完成] postId=abc123, status=PASSED, 耗时统计已记录
```

**缓存命中情况**：
```
[审核流程] 检查审核结果缓存: postId=abc123
[审核流程] 命中审核结果缓存，跳过重复审核: postId=abc123
```

**关键日志点**：
- 审核流程启动
- PostID提取
- 审核结果缓存检查
- 爬虫服务调用
- Agent审核调用
- 结果保存
- 流程完成统计

---

### 4. 爬虫服务阶段（三级缓存 + Playwright）

#### CrawlerService

```
[爬虫服务] 开始爬取: url=https://www.xiaohongshu.com/explore/abc123
[爬虫服务] 检查Redis缓存: postId=abc123
[爬虫服务] Redis缓存未命中
[爬虫服务] 检查PostgreSQL数据库: postId=abc123
[爬虫服务] PostgreSQL缓存未命中，需要执行爬虫
[爬虫服务] 开始Playwright爬取: postId=abc123
爬虫执行 - 尝试 1/3
模拟iPhone 13 Pro访问小红书
[爬虫服务] 同步保存到PostgreSQL: postId=abc123
[爬虫服务] 异步保存到Redis: postId=abc123
[爬虫服务完成] postId=abc123, title=这是一个测试标题
```

**Redis缓存命中**：
```
[爬虫服务] 开始爬取: url=https://www.xiaohongshu.com/explore/abc123
[爬虫服务] 检查Redis缓存: postId=abc123
[爬虫服务] Redis缓存命中: postId=abc123, 跳过爬取
```

**PostgreSQL缓存命中**：
```
[爬虫服务] 开始爬取: url=https://www.xiaohongshu.com/explore/abc123
[爬虫服务] 检查Redis缓存: postId=abc123
[爬虫服务] Redis缓存未命中
[爬虫服务] 检查PostgreSQL数据库: postId=abc123
[爬虫服务] PostgreSQL缓存命中: postId=abc123, 异步更新Redis
```

**关键日志点**：
- 爬取启动
- Redis缓存检查（第一级）
- PostgreSQL缓存检查（第二级）
- Playwright爬虫执行（第三级）
- 数据持久化（同步PostgreSQL + 异步Redis）
- 爬取完成

---

### 5. Agent审核阶段（LLM + Function Calling）

#### ContentAuditAgent

```
[Agent审核] 开始AI审核: postId=abc123, title=这是一个测试标题
[Agent审核] 构建System Prompt和User Message
[Agent审核] 调用LLM: model=gpt-4, temperature=0.3
[Agent审核] LLM原始响应: {"status":"REJECTED",...}
[Agent审核] 解析LLM返回的JSON决策
[Agent审核] 检测到问题: dimension=title, severity=HIGH, reason=标题包含敏感词
[Agent审核] 检测到问题: dimension=content, severity=MEDIUM, reason=内容涉嫌虚假宣传
[Agent审核完成] postId=abc123, status=REJECTED, confidence=0.88, riskLevel=HIGH
```

**审核通过情况**：
```
[Agent审核] 开始AI审核: postId=abc123, title=正常标题
[Agent审核] 构建System Prompt和User Message
[Agent审核] 调用LLM: model=gpt-4, temperature=0.3
[Agent审核] 解析LLM返回的JSON决策
[Agent审核完成] postId=abc123, status=PASSED, confidence=0.95, riskLevel=LOW
```

**关键日志点**：
- Agent审核启动
- Prompt构建
- LLM调用（模型、参数）
- 响应解析
- 各维度问题记录（title/content/tag/image）
- 审核决策（status、confidence、riskLevel）

---

## 日志查询示例

### 1. 追踪单个任务的完整流程

```bash
# 查看某个jobId的所有日志
tail -f /path/to/app.log | grep "jobId=AUDIT_20260128_001"

# 查看某个postId的所有日志
tail -f /path/to/app.log | grep "postId=abc123"
```

### 2. 监控异步任务进度

```bash
# 实时监控异步阶段
tail -f /path/to/app.log | grep "\[异步阶段\]"

# 查看任务进度更新
tail -f /path/to/app.log | grep "更新任务进度"
```

### 3. 检查缓存命中率

```bash
# Redis缓存命中
tail -f /path/to/app.log | grep "Redis缓存命中"

# PostgreSQL缓存命中
tail -f /path/to/app.log | grep "PostgreSQL缓存命中"

# 审核结果缓存命中
tail -f /path/to/app.log | grep "审核结果缓存"
```

### 4. 监控Agent审核结果

```bash
# 查看所有审核决策
tail -f /path/to/app.log | grep "\[Agent审核完成\]"

# 查看驳回的内容
tail -f /path/to/app.log | grep "status=REJECTED"

# 查看高风险内容
tail -f /path/to/app.log | grep "riskLevel=HIGH\|riskLevel=CRITICAL"
```

### 5. 统计成功率

```bash
# 统计审核完成情况
tail -1000 /path/to/app.log | grep "\[异步阶段完成\]"

# 示例输出：
# [异步阶段完成] jobId=AUDIT_20260128_001, status=COMPLETED, 总计=48, 成功=45, 失败=3
# 成功率 = 45/48 = 93.75%
```

---

## 日志配置

在 `application.yml` 中配置日志级别：

```yaml
logging:
  level:
    root: INFO
    com.xhs.audit: INFO
    com.xhs.audit.service: INFO
    com.xhs.audit.agent: INFO
    com.xhs.audit.infrastructure: INFO
    
  # 开发环境可以调整为DEBUG查看更多细节
  # com.xhs.audit.service.CrawlerService: DEBUG
  # com.xhs.audit.agent.ContentAuditAgent: DEBUG
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    
  file:
    name: logs/xhs-audit.log
    max-size: 100MB
    max-history: 30
```

---

## 性能日志

### 爬虫性能
```
爬虫完成: postId=abc123, 耗时=1234ms
爬虫耗时过长(>5s): 6789ms
```

### 任务处理进度
```
任务进度: jobId=AUDIT_20260128_001, completed=10/48
```

---

## 错误日志

### 单条链接审核失败
```
ERROR [AsyncAuditService] 单条链接审核失败: url=https://..., error=ERR_CRAWL_FAILED
```

### 审核任务失败
```
ERROR [AsyncAuditService] 审核任务失败: jobId=AUDIT_20260128_001
[异步阶段] 批处理完成，更新最终状态: jobId=AUDIT_20260128_001, PROCESSING -> FAILED
```

### Agent审核异常
```
ERROR [ContentAuditAgent] [Agent审核失败] postId=abc123, error=JSON解析失败
```

---

## 日志分析建议

1. **性能分析**：通过搜索 `耗时` 关键字，识别慢查询和性能瓶颈
2. **缓存效率**：统计 `缓存命中` vs `缓存未命中` 比例，优化缓存策略
3. **错误率监控**：统计 `ERROR` 级别日志，识别常见错误模式
4. **审核质量**：分析 `confidence` 和 `riskLevel` 分布，调优Agent参数
5. **并发监控**：观察 `[异步阶段]` 日志的线程分布，确保线程池配置合理

---

## 总结

通过以上日志结构，流程图中的每个阶段都可以在日志中清晰追踪：

| 流程阶段 | 日志标签 | 关键信息 |
|---------|---------|---------|
| Excel上传与解析 | `[同步阶段]` | 文件信息、链接数量、去重结果 |
| 任务创建与提交 | `[同步阶段]` | jobId、任务状态、总链接数 |
| 异步批处理 | `[异步阶段]` | 处理进度、成功/失败统计 |
| 单条审核流程 | `[审核流程]` | 缓存检查、爬虫调用、Agent审核 |
| 内容爬取 | `[爬虫服务]` | 三级缓存、Playwright执行 |
| AI审核 | `[Agent审核]` | LLM调用、审核决策、问题详情 |

每个日志标签都对应流程图中的特定节点，便于问题定位和流程追踪。
