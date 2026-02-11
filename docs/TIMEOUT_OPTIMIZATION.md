# 审核超时优化方案

## 问题现象

```
TimeoutException at ContentAuditAgent.auditContent:159
审核任务在 60 秒内未完成，导致超时异常
```

## 根因分析

1. **硬编码超时配置**
   - 之前使用固定的 60 秒超时（`AUDIT_TIMEOUT_SECONDS`）
   - 文本和图片审核使用相同的超时时间
   - 无法根据实际情况灵活调整

2. **视觉模型响应慢**
   - 图片审核（Vision API）通常比文本审核耗时更长
   - 图片需要下载、压缩、Base64 编码等预处理
   - 视觉模型推理时间本身较长

3. **日志不够详细**
   - 缺少各阶段耗时统计
   - 难以定位具体瓶颈

## 优化方案

### 1. 独立超时配置 ✅

**代码修改**：
- 移除硬编码常量 `AUDIT_TIMEOUT_SECONDS`
- 新增两个可配置参数：
  - `audit.llm.text-timeout-seconds`（默认 90 秒）
  - `audit.llm.image-timeout-seconds`（默认 120 秒）

**配置文件**（`application.yml` 和 `application-audit.yml`）：
```yaml
audit:
  llm:
    text-timeout-seconds: 90   # 文本审核超时
    image-timeout-seconds: 120 # 图片审核超时（视觉模型通常更慢）
```

**优点**：
- ✅ 图片审核获得更长的等待时间
- ✅ 可根据生产环境实际情况动态调整
- ✅ 不同环境可使用不同配置

### 2. 详细的耗时日志 ✅

**新增日志点**：

#### 文本审核
```
[文本审核] 开始执行文本审核: postId=xxx
[文本审核] 完成: postId=xxx, LLM耗时=2500ms, 总耗时=2520ms
```

#### 图片审核
```
[图片审核] 开始审核图片: postId=xxx, imageCount=3
[图片审核] 图片下载+压缩耗时: 850ms
[图片审核] Vision API调用耗时: 4200ms
[图片审核] 完成: postId=xxx, 总耗时=5100ms
```

#### 并行审核总览
```
[Agent审核] 并行执行文本审核和图片审核... (文本超时:90s, 图片超时:120s)
[Agent审核] 文本审核完成，耗时: 2520ms
[Agent审核] 图片审核完成，耗时: 5100ms
[Agent审核] 并行审核总耗时: 5150ms
```

**优点**：
- ✅ 清晰展示每个阶段耗时
- ✅ 便于识别性能瓶颈
- ✅ 支持生产环境性能分析

### 3. 优化的异常处理 ✅

**专门处理超时异常**：
```java
catch (java.util.concurrent.TimeoutException e) {
    log.error("[Agent审核超时] postId={}, timeout={}s, 建议检查LLM服务响应速度",
            content.getPostId(),
            Math.max(textAuditTimeoutSeconds, imageAuditTimeoutSeconds));
    return AuditDecision.uncertain(content.getPostId(),
            "审核超时，请稍后重试");
}
```

**优点**：
- ✅ 区分超时和其他异常
- ✅ 提供更明确的错误信息
- ✅ 便于监控和告警

### 4. Resilience4j 配置优化 ✅

**更新熔断器慢调用阈值**：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      llmService:
        slow-call-duration-threshold: 60s  # 从 30s 提高到 60s
```

**优点**：
- ✅ 与新的超时配置保持一致
- ✅ 避免误触发熔断器

## 部署建议

### 1. 配置调优

根据实际环境调整超时时间：

**开发环境**（本地 LLM）：
```yaml
audit:
  llm:
    text-timeout-seconds: 60
    image-timeout-seconds: 90
```

**生产环境**（远程 LLM，可能有网络延迟）：
```yaml
audit:
  llm:
    text-timeout-seconds: 120
    image-timeout-seconds: 180
```

**高负载环境**：
```yaml
audit:
  llm:
    text-timeout-seconds: 150
    image-timeout-seconds: 240
```

### 2. 监控指标

建议监控以下指标：

1. **审核耗时分布**
   - P50、P95、P99 耗时
   - 文本审核 vs 图片审核

2. **超时率**
   - 超时次数 / 总审核次数
   - 按内容类型分组统计

3. **LLM 服务健康度**
   - API 响应时间
   - 错误率
   - 熔断器状态

### 3. 告警规则

```yaml
# 示例告警配置
alerts:
  - name: 审核超时率过高
    condition: timeout_rate > 5%
    action: 通知运维团队检查 LLM 服务

  - name: 审核耗时异常
    condition: p95_duration > 60s
    action: 触发性能分析

  - name: 熔断器开启
    condition: circuit_breaker == OPEN
    action: 立即通知，可能需要人工介入
```

## 优化效果预期

### 前后对比

| 指标 | 优化前 | 优化后 |
|-----|-------|-------|
| 文本审核超时 | 60s（硬编码） | 90s（可配置） |
| 图片审核超时 | 60s（硬编码） | 120s（可配置） |
| 超时错误日志 | 通用异常 | 专门的超时日志 |
| 性能追踪 | 无详细耗时 | 各阶段耗时统计 |
| 熔断器阈值 | 30s | 60s（匹配新配置） |

### 预期改进

1. **降低超时率** - 预计从 5-10% 降低到 1-2%
2. **更精准的性能诊断** - 可快速定位瓶颈（下载、压缩、LLM）
3. **更灵活的配置** - 不同环境使用不同超时策略
4. **更好的稳定性** - 避免误触发熔断器

## 进一步优化方向

### 1. 异步化改进 🔄

当前是并行执行文本和图片审核，但仍需等待两者都完成。可以考虑：
- 文本审核优先，图片审核异步补充
- 实现"快速通过"模式：文本审核通过即可先返回

### 2. 缓存优化 🔄

- ✅ 已实现图片 Base64 缓存
- 🔄 可增加 LLM 审核结果缓存（相同内容）
- 🔄 可增加图片特征缓存（避免重复下载）

### 3. 降级策略 🔄

当 LLM 服务不可用或超时时：
- 仅使用规则引擎审核
- 标记为"待人工复审"
- 使用备用的轻量级模型

### 4. 批处理优化 🔄

- 批量处理多张图片（如果内容有多图）
- 批量调用 LLM API（提高吞吐量）

## 回滚方案

如果优化后出现问题，可快速回滚：

```yaml
# 恢复到原来的配置
audit:
  llm:
    text-timeout-seconds: 60
    image-timeout-seconds: 60
```

或者使用环境变量覆盖：
```bash
export AUDIT_LLM_TEXT_TIMEOUT_SECONDS=60
export AUDIT_LLM_IMAGE_TIMEOUT_SECONDS=60
```

## 总结

本次优化主要解决了审核超时问题，通过：
1. ✅ 独立配置文本和图片审核超时
2. ✅ 增加详细的耗时日志
3. ✅ 优化异常处理和熔断器配置

**关键改进点**：
- 图片审核获得更长等待时间（从 60s → 120s）
- 所有超时配置可通过 YAML 灵活调整
- 详细的性能日志便于问题诊断

**建议后续动作**：
1. 部署到测试环境观察效果
2. 根据实际耗时数据调优超时配置
3. 建立监控和告警机制
4. 考虑实施进一步的异步化和缓存优化

---

*文档生成时间: 2026-02-08*  
*版本: v4.1*
