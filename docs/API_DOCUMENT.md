# XHS Audit System - API 文档

完整的小红书内容审核系统REST API接口文档。

## 📋 基础信息

- **Base URL**: `http://localhost:8080/api/v1/audit`
- **响应格式**: JSON
- **认证**: 暂无认证要求（生产环境建议添加）

## 📝 响应格式标准

所有响应遵循统一的格式：

```json
{
  "code": "000000",           // 状态码，000000表示成功
  "message": "success",       // 消息描述
  "data": {},                 // 响应数据
  "timestamp": "2026-01-27T15:40:10"  // 时间戳
}
```

### 状态码说明

| 代码 | 含义 | HTTP状态 |
|------|------|---------|
| 000000 | 成功 | 200 |
| ERR_* | 业务错误 | 400 |
| ERR_*_NOT_FOUND | 资源不存在 | 404 |
| 其他 | 系统错误 | 500 |

---

## 🔍 API 端点

### 1. 单条内容审核

**POST** `/content`

实时审核单条小红书内容。系统会自动爬取内容并通过AI进行审核。

#### 请求

```bash
curl -X POST http://localhost:8080/api/v1/audit/content \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.xiaohongshu.com/explore/abc123def",
    "forceRefresh": false
  }'
```

#### 请求体

```json
{
  "url": "https://www.xiaohongshu.com/explore/abc123def",
  "forceRefresh": false  // true: 忽略缓存重新审核; false: 优先返回缓存结果
}
```

#### 响应 (200 OK)

```json
{
  "code": "000000",
  "message": "success",
  "data": {
    "postId": "abc123def",
    "url": "https://www.xiaohongshu.com/explore/abc123def",
    "status": "PASSED",           // PASSED / REJECTED / UNCERTAIN
    "reasons": [                  // 驳回原因（仅status=REJECTED时有值）
      {
        "dimension": "content",
        "reason": "发现虚假广告特征",
        "severity": "HIGH"        // LOW / MEDIUM / HIGH / CRITICAL
      }
    ],
    "confidenceScore": 0.95,      // 置信度 0-1
    "suggestedAction": "ACCEPT",  // ACCEPT / REJECT / REVIEW
    "riskLevel": "LOW",           // LOW / MEDIUM / HIGH / CRITICAL
    "modelName": "gpt-4",
    "auditedTime": "2026-01-27T15:40:10"
  },
  "timestamp": "2026-01-27T15:40:10"
}
```

#### 错误响应 (400/500)

```json
{
  "code": "ERR_URL_INVALID",
  "message": "URL格式不符合小红书链接规范",
  "timestamp": "2026-01-27T15:40:10"
}
```

---

### 2. 批量同步审核 (仅测试用)

**POST** `/batch`

批量提交小红书链接进行同步审核。

⚠️ **注意**: 这是同步接口，仅用于测试和小批量处理（<10条）。  
生产环境请使用 `/upload` 端点上传Excel，系统会异步处理。

#### 请求

```bash
curl -X POST http://localhost:8080/api/v1/audit/batch \
  -H "Content-Type: application/json" \
  -d '{
    "links": [
      "https://www.xiaohongshu.com/explore/xxx",
      "https://www.xiaohongshu.com/explore/yyy"
    ]
  }'
```

#### 请求体

```json
{
  "links": [
    "https://www.xiaohongshu.com/explore/xxx",
    "https://www.xiaohongshu.com/explore/yyy",
    "https://www.xiaohongshu.com/explore/zzz"
  ]  // 最多100条
}
```

#### 响应 (200 OK)

```json
{
  "code": "000000",
  "data": {
    "totalCount": 3,
    "passedCount": 2,
    "rejectedCount": 1,
    "results": [
      { /* AuditDecision对象 */ },
      { /* AuditDecision对象 */ },
      { /* AuditDecision对象 */ }
    ]
  }
}
```

---

### 3. 上传Excel文件

**POST** `/upload`

上传包含小红书链接的Excel文件。系统自动提取链接、去重，并创建异步批审核任务。

#### 请求

```bash
curl -X POST http://localhost:8080/api/v1/audit/upload \
  -F "file=@links.xlsx"
```

#### 请求参数

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| file | File | ✓ | Excel文件 (.xlsx或.xls)，最大50MB |

#### 响应 (200 OK)

```json
{
  "code": "000000",
  "data": {
    "jobId": "job-2026-01-27-550e8400-e29b-41d4-a716-446655440000",
    "message": "文件上传成功，审核任务已创建"
  }
}
```

#### 错误响应 (400)

```json
{
  "code": "ERR_FILE_FORMAT",
  "message": "仅支持.xlsx和.xls格式",
  "timestamp": "2026-01-27T15:40:10"
}
```

---

### 4. 查询任务进度

**GET** `/job/{jobId}`

查询异步批审核任务的实时进度和状态。

#### 请求

```bash
curl http://localhost:8080/api/v1/audit/job/job-2026-01-27-550e8400-e29b-41d4-a716-446655440000
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| jobId | String | 上传Excel时返回的任务ID |

#### 响应 (200 OK)

```json
{
  "code": "000000",
  "data": {
    "jobId": "job-2026-01-27-550e8400-e29b-41d4-a716-446655440000",
    "totalLinks": 100,
    "completedCount": 85,
    "passedCount": 75,
    "rejectedCount": 10,
    "status": "PROCESSING",         // PENDING / PROCESSING / COMPLETED / PARTIAL_SUCCESS / FAILED
    "progressPercent": 85,
    "createdTime": "2026-01-27T15:30:00",
    "estimatedCompletionTime": null,
    "errorCount": 0,
    "errorSummary": []
  }
}
```

#### 任务状态说明

| 状态 | 说明 |
|------|------|
| PENDING | 等待处理 |
| PROCESSING | 处理中 |
| COMPLETED | 全部完成 |
| PARTIAL_SUCCESS | 部分失败 |
| FAILED | 任务失败 |

---

### 5. 获取审核结果

**GET** `/result/{postId}`

获取指定帖子的完整审核结果。

#### 请求

```bash
curl http://localhost:8080/api/v1/audit/result/abc123def
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| postId | String | 小红书帖子ID |

#### 响应 (200 OK)

```json
{
  "code": "000000",
  "data": {
    "postId": "abc123def",
    "url": "https://www.xiaohongshu.com/explore/abc123def",
    "status": "PASSED",
    "reasons": [],
    "confidenceScore": 0.98,
    "suggestedAction": "ACCEPT",
    "riskLevel": "LOW",
    "modelName": "gpt-4",
    "auditedTime": "2026-01-27T15:25:00"
  }
}
```

---

### 6. 下载审核结果Excel

**GET** `/download/{jobId}`

下载指定任务的审核结果Excel文件。任务必须处于COMPLETED或PARTIAL_SUCCESS状态。

#### 请求

```bash
curl -O http://localhost:8080/api/v1/audit/download/job-2026-01-27-550e8400-e29b-41d4-a716-446655440000
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| jobId | String | 任务ID |

#### 查询参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|-------|------|
| format | String | xlsx | 文件格式（仅支持xlsx） |

#### 响应 (200 OK)

- Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Content-Disposition: `attachment; filename=audit_result_job-xxx.xlsx`
- 响应体: Excel二进制文件

#### Excel列结构

| 序号 | 列名 | 类型 | 说明 |
|------|------|------|------|
| 1 | 序号 | Integer | 行号 |
| 2 | 链接 | String | 小红书帖子链接 |
| 3 | 审核状态 | String | 通过/驳回/待定 |
| 4 | 驳回原因 | String | 多个原因用;分隔 |
| 5 | 置信度 | Double | 0-1之间的数值 |
| 6 | 审核时间 | DateTime | yyyy-MM-dd HH:mm:ss |

---

### 7. 系统健康检查

**GET** `/health`

检查系统各组件的运行状态。

#### 请求

```bash
curl http://localhost:8080/api/v1/audit/health
```

#### 响应 (200 OK)

```json
{
  "code": "000000",
  "data": {
    "status": "UP",
    "timestamp": "2026-01-27T15:40:10",
    "components": {
      "database": {
        "status": "UP",
        "details": "Connected"
      },
      "redis": {
        "status": "UP",
        "details": "PONG"
      },
      "playwright": {
        "status": "UP",
        "details": "3 instances ready"
      }
    }
  }
}
```

---

## 🔄 工作流程示例

### 使用流程

#### 方式1: 单条审核（实时）

```bash
# 1. 提交单条链接审核
curl -X POST http://localhost:8080/api/v1/audit/content \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.xiaohongshu.com/explore/xxx"}'

# 响应立即返回审核结果
```

#### 方式2: 批量审核（同步）

```bash
# 1. 提交多条链接
curl -X POST http://localhost:8080/api/v1/audit/batch \
  -H "Content-Type: application/json" \
  -d '{"links": ["url1", "url2", "url3"]}'

# 响应立即返回所有审核结果
```

#### 方式3: 批量审核（异步推荐）

```bash
# 1. 上传Excel文件
curl -X POST http://localhost:8080/api/v1/audit/upload \
  -F "file=@links.xlsx"
  
# 返回: { "jobId": "job-xxx" }

# 2. 轮询查询进度
for i in {1..100}; do
  curl http://localhost:8080/api/v1/audit/job/job-xxx
  sleep 5
done

# 3. 任务完成后下载结果
curl -O http://localhost:8080/api/v1/audit/download/job-xxx
```

---

## 🔒 错误处理

### 常见错误码

| 错误码 | HTTP状态 | 说明 | 解决方案 |
|--------|---------|------|---------|
| ERR_URL_INVALID | 400 | URL格式错误 | 检查URL是否为有效的小红书链接 |
| ERR_FILE_FORMAT | 400 | 文件格式错误 | 仅支持.xlsx和.xls |
| ERR_FILE_SIZE_EXCEED | 400 | 文件过大 | 最大50MB |
| ERR_FILE_PARSE_FAILED | 400 | 文件解析失败 | 检查Excel格式是否正确 |
| ERR_NO_VALID_LINKS | 400 | 没有找到有效链接 | Excel中需至少包含一条有效链接 |
| ERR_CRAWL_FAILED | 500 | 爬虫失败 | 检查网络连接或链接有效性 |
| ERR_AUDIT_FAILED | 500 | 审核失败 | 检查AI服务配置 |
| ERR_JOB_NOT_FOUND | 404 | 任务不存在 | 检查jobId是否正确 |
| ERR_RESULT_NOT_FOUND | 404 | 审核结果不存在 | 该链接尚未被审核 |
| ERR_JOB_NOT_COMPLETED | 400 | 任务未完成 | 任务仍在处理中或失败 |

### 错误响应示例

```json
{
  "code": "ERR_URL_INVALID",
  "message": "URL格式不符合小红书链接规范，请检查链接是否正确",
  "timestamp": "2026-01-27T15:40:10"
}
```

---

## 📊 审核维度详解

系统从以下几个维度对内容进行AI审核：

### 1. 标题审核 (title)
- 敏感词检测
- 违规内容识别
- 标题党检测

### 2. 内容审核 (content)
- 隐式营销检测（微信、代理等）
- 虚假广告识别（100%保证、绝对等）
- 标题党识别（震惊、惊呆等）

### 3. 标签审核 (tag)
- 不合规标签过滤
- 敏感标签识别

### 4. 敏感词检测
- 关键词快速匹配
- 多维度覆盖

---

## ⚙️ 性能指标

| 指标 | 值 |
|------|-----|
| 单条审核响应时间 | 1-3s |
| 批量审核吞吐量 | 100+ 链接/分钟 |
| 缓存命中率 | >90% |
| 系统可用性 | 99.9% |
| 最大并发连接 | 1000+ |

---

## 🔐 生产环境建议

1. **添加API认证** - 使用JWT或OAuth
2. **速率限制** - 防止滥用
3. **请求签名** - 验证请求完整性
4. **HTTPS** - 加密传输
5. **监控告警** - 实时监控系统状态
6. **日志记录** - 审计所有API调用

---

## 📞 联系方式

- 技术支持: support@example.com
- Bug报告: bugs@example.com
- 功能建议: features@example.com

