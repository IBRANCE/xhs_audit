# 基础规则验证器使用指南

## 概述

基础规则验证器在 AI 审核前进行快速规则检查，过滤不符合基本要求的内容，减少不必要的 LLM 调用。

## 验证规则

### 规则 1: 内容格式要求
- ✅ 正文文字 ≥ 25 字（去除空白符后计算）
- ✅ 图片数量 ≥ 1 张

### 规则 2: 必带话题标签
必须包含以下 3 个标签：
1. `#东风日产`
2. `#尽兴由NI`
3. 车型名称标签（如：`#日产N7`、`#天籁`、`#启辰`等）

支持的车型关键词：
- **日产品牌**：天籁、轩逸、逍客、奇骏、X-TRAIL、ARIYA、艾睿雅、N7、N6、探陆、NISSAN
- **启辰品牌**：启辰大V、启辰星、启辰D60、启辰、Venucia
- **英菲尼迪品牌**：QX50、QX60、Q50L、英菲尼迪、INFINITI

## 工作流程

```
内容提交
    ↓
基础规则验证 (ContentRuleValidator)
    ↓
    ├─ 规则不通过 → 立即返回 REJECTED（快速失败）
    │   - 置信度：1.0
    │   - 模型名称：RuleValidator
    │   - 响应时间：< 100ms
    │
    └─ 规则通过 → 继续 LLM 审核 (ContentAuditAgent)
        - 并行执行文本和图片审核
        - 响应时间：5-10s
```

## 配置项

在 `application-local.yml` 中配置：

```yaml
audit:
  rules:
    min-text-length: 25      # 最小正文字数
    min-image-count: 1       # 最小图片数量
```

## 日志示例

### 规则验证通过
```
[规则验证] 开始验证: postId=123456, title=东风日产N7试驾体验
[规则验证] 检测到车型标签: N7
[规则验证] 验证通过: postId=123456
[Agent审核] 基础规则验证通过，继续LLM审核: postId=123456
```

### 规则验证失败
```
[规则验证] 开始验证: postId=123456, title=短内容
[规则验证] 正文文字不足25字（当前: 10字）: postId=123456
[规则验证] 缺少必带话题标签: #东风日产、#尽兴由NI: postId=123456
[规则验证] 验证失败: postId=123456, 原因数=2
[规则验证]   - [content_format] 正文文字不足25字（当前: 10字） (severity: HIGH)
[规则验证]   - [tag] 缺少必带话题标签: #东风日产、#尽兴由NI (severity: CRITICAL)
[Agent审核] 基础规则验证失败，快速驳回: postId=123456, reasons=2
```

## 驳回原因类型

### content_format（内容格式）
- 正文文字不足 X 字
- 图片数量不足 X 张
- 严重程度：`HIGH`

### tag（标签）
- 未包含任何话题标签
- 缺少必带话题标签：#东风日产、#尽兴由NI
- 缺少车型名称标签
- 严重程度：`CRITICAL`

## 性能收益

- ⚡ **快速失败**：规则不通过的内容在 < 100ms 内返回结果
- 💰 **成本节约**：预计减少 30-50% 的 LLM 调用
- 🎯 **准确判断**：规则验证置信度 100%，无误判
- 📊 **易于追踪**：`modelName=RuleValidator` 便于统计分析

## 测试

运行规则验证器测试：

```bash
mvn test -Dtest=ContentRuleValidatorTest
```

测试覆盖场景：
- ✅ 所有规则通过
- ❌ 正文字数不足
- ❌ 图片数量不足
- ❌ 缺少必带标签
- ❌ 缺少车型标签
- ✅ 标签带/不带 # 号
- ✅ 标签大小写混合
- ❌ 多个规则同时失败

## API 响应示例

### 规则验证失败响应

```json
{
  "postId": "123456",
  "url": "https://www.xiaohongshu.com/explore/123456",
  "status": "REJECTED",
  "reasons": [
    {
      "dimension": "content_format",
      "reason": "正文文字不足25字（当前: 10字）",
      "severity": "HIGH"
    },
    {
      "dimension": "tag",
      "reason": "缺少必带话题标签: #东风日产、#尽兴由NI",
      "severity": "CRITICAL"
    }
  ],
  "confidenceScore": 1.0,
  "suggestedAction": "驳回",
  "riskLevel": "HIGH",
  "modelName": "RuleValidator",
  "auditedTime": "2026-02-08T19:00:00"
}
```

## 实施文件

- **服务类**：`src/main/java/com/xhs/audit/service/ContentRuleValidator.java`
- **集成代码**：`src/main/java/com/xhs/audit/agent/ContentAuditAgent.java`
- **配置文件**：`src/main/resources/application-local.yml`
- **单元测试**：`src/test/java/com/xhs/audit/service/ContentRuleValidatorTest.java`

## 注意事项

1. **标签匹配容错**：自动去除 `#` 号、空格，大小写不敏感
2. **排除必带标签**：`#东风日产` 和 `#尽兴由NI` 不被算作车型标签
3. **子串匹配**：标签只需包含车型关键词即可（如 `#日产N7试驾` 包含 "N7"）
4. **字数计算**：使用 `trim()` 后的长度，自动去除首尾空白符

## 扩展建议

如需添加新的车型或规则：

1. **添加车型**：在 `CAR_MODEL_NAMES` 列表中追加
2. **修改字数限制**：通过配置项 `audit.rules.min-text-length` 调整
3. **修改图片数量**：通过配置项 `audit.rules.min-image-count` 调整
4. **添加新规则**：在 `validateRules()` 方法中添加新的验证逻辑

---

**版本**: v1.0.0  
**日期**: 2026-02-08  
**作者**: XHS Audit System
