# 本周工作计划 - Week 2 (2026-02-03 至 2026-02-09)

## 📅 周目标

**总目标**: 完成Phase 2.3和Phase 2.4，建立完整的审核智能体

**工作量**: 4.5天 (周二至周六)  
**预计代码**: 1,490行  
**测试覆盖**: ≥85%  

---

## 📋 详细任务分解

### 【Phase 2.3】AuditRuleFunctions (2天)

#### 周二 2026-02-03 (Day 1)

**目标**: 完成核心架构设计和代码实现

```
⏰ 08:00-09:00 (1h) - 准备阶段
├─ 任务: 检查依赖和环境
├─ 检查清单:
│  ├─ pom.xml中Spring AI库是否正确 ✓
│  ├─ Caffeine缓存库是否导入
│  ├─ AuditRule/SensitiveWord实体是否存在
│  ├─ 测试框架是否配置完成
│  └─ PostgreSQL数据库连接正常
└─ 输出: 绿灯通过,准备开始

⏰ 09:00-12:00 (3h) - 设计和编码
├─ 09:00-10:00: 设计DTO类 (AuditRuleFunction/SensitiveWordFunction)
│  ├─ 输入: Function的入参结构
│  ├─ 输出: Function的返回值结构
│  ├─ 注解: @FunctionProperty和@JsonProperty
│  └─ 文件: src/main/java/com/xhs/audit/ai/function/dto/ (3个文件)
│
├─ 10:00-11:00: 实现AuditRuleFunctions类
│  ├─ getAuditRules() Bean实现
│  ├─ checkSensitiveWords() Bean实现
│  ├─ 异常处理和日志
│  └─ 文件: src/main/java/com/xhs/audit/ai/function/AuditRuleFunctions.java
│
└─ 11:00-12:00: Caffeine缓存配置
   ├─ rulesCache Bean (1小时TTL)
   ├─ sensitiveWordsCache Bean (2小时TTL)
   └─ 文件: src/main/java/com/xhs/audit/config/CacheConfiguration.java

⏰ 14:00-17:00 (3h) - 单元测试
├─ 14:00-15:00: 测试框架搭建
│  ├─ Mock对象创建
│  ├─ Test Data Factory
│  └─ 断言工具函数
│
├─ 15:00-16:00: 编写核心测试用例
│  ├─ testGetAuditRulesSuccess
│  ├─ testCheckSensitiveWordsWithMatch
│  └─ testCacheHitRate
│
└─ 16:00-17:00: 验证编译和测试通过
   ├─ mvn clean compile
   ├─ mvn test
   └─ 代码覆盖率检查

📊 Day 1 预期成果:
  ✓ 3个DTO类 (150-180行)
  ✓ AuditRuleFunctions实现 (180-200行)
  ✓ CacheConfiguration配置 (80-100行)
  ✓ 4-5个单元测试用例
  ✓ 编译成功,测试通过率 ≥80%
```

#### 周三 2026-02-04 (Day 2)

**目标**: 完成测试、集成和文档

```
⏰ 08:00-10:00 (2h) - 完成测试
├─ 08:00-09:00: 补充单元测试
│  ├─ testAnalyzeContentMultiDimension
│  ├─ testCacheInvalidation
│  ├─ testErrorHandling
│  └─ 目标: 代码覆盖率 ≥85%
│
└─ 09:00-10:00: 集成测试
   ├─ testEndToEndRuleRetrieval (真实数据库)
   ├─ testConcurrentAccess
   └─ testPerformance

⏰ 10:00-11:30 (1.5h) - 代码审查和优化
├─ 10:00-10:30: 自我审查
│  ├─ 检查Javadoc完整性
│  ├─ 检查异常处理完整性
│  ├─ 检查日志记录充分性
│  └─ 检查遵循SOLID原则
│
└─ 10:30-11:30: 性能优化
   ├─ 检查缓存TTL是否合理
   ├─ 检查查询性能 (检查慢查询)
   └─ 添加性能监控日志

⏰ 14:00-16:00 (2h) - 文档编写
├─ 14:00-14:30: 实现文档
│  ├─ Function定义说明
│  ├─ 缓存策略详解
│  └─ 文件: PHASE_2_3_IMPLEMENTATION.md
│
├─ 14:30-15:00: API使用示例
│  ├─ 集成示例代码
│  ├─ 常见问题解答
│  └─ 故障排查指南
│
└─ 15:00-16:00: 最终验证
   ├─ mvn clean compile
   ├─ mvn test
   ├─ 验证无编译警告
   └─ 验证所有测试通过

⏰ 16:00-17:00 (1h) - 提交和总结
├─ git add / commit
├─ 更新进度文档
├─ 准备Phase 2.4

📊 Day 2 预期成果:
  ✓ 总测试用例 ≥8个
  ✓ 代码覆盖率 ≥85%
  ✓ 完成PHASE_2_3_IMPLEMENTATION.md文档
  ✓ BUILD SUCCESS
  ✓ 准备开始Phase 2.4
```

**Phase 2.3 验收标准**:
- [ ] 所有单元测试通过 (100%)
- [ ] 代码覆盖率 ≥85%
- [ ] 编译成功,无警告
- [ ] 与CrawlerService集成成功
- [ ] 文档完整

---

### 【Phase 2.4】ContentAuditAgent (2.5天)

#### 周四 2026-02-05 (Day 1)

**目标**: 完成System Prompt和ChatClient配置

```
⏰ 08:00-09:00 (1h) - 环境准备
├─ 检查OpenAI API密钥配置
├─ 验证ChatClient能否注入
├─ 检查Spring AI库版本
└─ 验证AuditRuleFunctions可用

⏰ 09:00-12:00 (3h) - Prompt设计和DTO
├─ 09:00-10:00: System Prompt设计
│  ├─ 审核规则说明
│  ├─ 决策标准定义
│  ├─ 审核维度详解
│  └─ 文件: AuditPromptBuilder.java (120行)
│
├─ 10:00-11:00: DTO类设计
│  ├─ AuditDecision DTO (包含验证)
│  ├─ AuditContext DTO
│  ├─ 枚举定义 (Decision/RiskLevel)
│  └─ 文件: src/main/java/com/xhs/audit/ai/dto/ (2-3个文件)
│
└─ 11:00-12:00: ChatClient配置
   ├─ AuditAgentConfiguration
   ├─ Function Calling注册
   ├─ OpenAI选项配置
   └─ 文件: src/main/java/com/xhs/audit/config/AuditAgentConfiguration.java

⏰ 14:00-17:00 (3h) - 核心Agent实现
├─ 14:00-15:30: ContentAuditAgent实现
│  ├─ auditContent() 主方法
│  ├─ 构建审核消息
│  ├─ 调用ChatClient
│  ├─ 解析LLM响应
│  └─ 保存审核结果
│
├─ 15:30-16:30: 异常处理
│  ├─ AuditException定义
│  ├─ 错误降级逻辑
│  ├─ 重试机制 (可选)
│  └─ 日志记录完整性
│
└─ 16:30-17:00: 初步编译验证
   ├─ mvn clean compile
   ├─ 检查无编译错误
   └─ 记录任何问题

📊 Day 1 预期成果:
  ✓ AuditPromptBuilder.java (120行)
  ✓ AuditDecision + AuditContext DTO (140行)
  ✓ AuditAgentConfiguration (140行)
  ✓ ContentAuditAgent核心实现 (200行)
  ✓ 代码编译无错误
  ✓ 总代码: ~600行
```

#### 周五 2026-02-06 (Day 2)

**目标**: 完成测试和集成验证

```
⏰ 08:00-09:00 (1h) - 补完实现
├─ 完成ContentAuditAgent遗漏的方法
├─ 添加批量处理支持
└─ 完成所有private方法

⏰ 09:00-12:00 (3h) - 单元测试
├─ 09:00-10:00: 单元测试框架
│  ├─ Mock ChatClient
│  ├─ Mock Repository
│  └─ Test Data Factory
│
├─ 10:00-11:00: 编写测试用例
│  ├─ testAuditContentApproved
│  ├─ testAuditContentRejected
│  ├─ testAuditContentFlagged
│  ├─ testBatchAudit
│  └─ 目标: 4-5个测试用例
│
└─ 11:00-12:00: 处理测试失败
   ├─ Debug模型响应
   ├─ 调整Mock数据
   └─ 验证所有测试通过

⏰ 14:00-16:00 (2h) - 集成测试
├─ 14:00-15:00: 端到端集成测试
│  ├─ CrawlerService → ContentAuditAgent pipeline
│  ├─ 数据库保存验证
│  ├─ 缓存一致性验证
│  └─ 异常场景测试
│
└─ 15:00-16:00: 性能测试
   ├─ 单条审核延迟测试 (<2s)
   ├─ 批量吞吐测试 (>30条/秒)
   └─ 性能基准建立

⏰ 16:00-17:00 (1h) - 文档
├─ PHASE_2_4_IMPLEMENTATION.md编写
├─ 使用示例编写
├─ API文档编写
└─ 故障排查指南编写

📊 Day 2 预期成果:
  ✓ 6-8个单元测试
  ✓ 2-3个集成测试
  ✓ 性能基准数据
  ✓ 完整的实现文档
  ✓ 所有测试通过,覆盖率 ≥85%
  ✓ 总代码: ~840行
```

#### 周六 2026-02-07 (Day 0.5)

**目标**: 最终验证和准备

```
⏰ 08:00-10:00 (2h) - 最终验证
├─ 08:00-08:30: 代码审查
│  ├─ Javadoc完整性
│  ├─ 异常处理完整性
│  ├─ 日志记录完整性
│  └─ 代码规范检查
│
├─ 08:30-09:00: 编译验证
│  ├─ mvn clean compile
│  ├─ mvn test (所有测试)
│  ├─ 检查无编译警告
│  └─ 检查无测试失败
│
└─ 09:00-10:00: 集成验证
   ├─ 启动PostgreSQL和Redis
   ├─ 运行完整的E2E测试
   ├─ 验证数据库状态
   └─ 验证API响应

⏰ 10:00-12:00 (2h) - 文档和提交
├─ 10:00-11:00: 更新所有文档
│  ├─ README.md更新
│  ├─ PROGRESS.md更新
│  ├─ ROADMAP_2026.md更新
│  └─ 创建WEEKLY_SUMMARY.md
│
└─ 11:00-12:00: Git提交
   ├─ git add .
   ├─ git commit (详细消息)
   ├─ 准备下周计划
   └─ 准备Phase 2.5

📊 Day 0.5 预期成果:
  ✓ 完整的代码审查通过
  ✓ mvn clean compile BUILD SUCCESS
  ✓ 所有测试通过 (100%)
  ✓ E2E验证成功
  ✓ Phase 2.4完成
  ✓ 准备下周Phase 2.5
```

**Phase 2.4 验收标准**:
- [ ] 所有单元测试通过 (100%)
- [ ] 代码覆盖率 ≥85%
- [ ] 编译成功,无警告
- [ ] E2E测试通过
- [ ] 单条审核耗时 <2s
- [ ] 批量吞吐 >30条/秒
- [ ] 文档完整

---

## 📊 周进度追踪

### 代码行数预计

| 组件 | 预计行数 | 状态 |
|------|---------|------|
| Phase 2.3 DTO | 150-180 | 计划中 |
| AuditRuleFunctions | 180-200 | 计划中 |
| CacheConfiguration | 80-100 | 计划中 |
| Phase 2.3 测试 | 150-200 | 计划中 |
| **Phase 2.3 小计** | **560-680** | **计划中** |
| | | |
| AuditPromptBuilder | 120 | 计划中 |
| AuditDecision DTO | 80 | 计划中 |
| AuditAgentConfiguration | 140 | 计划中 |
| ContentAuditAgent | 200 | 计划中 |
| AuditException | 40 | 计划中 |
| Phase 2.4 测试 | 300 | 计划中 |
| **Phase 2.4 小计** | **880** | **计划中** |
| | | |
| **周总计** | **~1,490** | **计划中** |

### 测试预计

| 类型 | Phase 2.3 | Phase 2.4 | 总计 |
|------|-----------|-----------|------|
| 单元测试用例 | 8-10 | 6-8 | 14-18 |
| 集成测试用例 | 1-2 | 2-3 | 3-5 |
| 性能测试 | 是 | 是 | 是 |
| 预期通过率 | 100% | 100% | 100% |

### 质量指标预计

| 指标 | Phase 2.3 | Phase 2.4 | 目标 |
|------|-----------|-----------|------|
| 代码覆盖率 | ≥85% | ≥85% | ≥85% |
| 编译警告 | 0 | 0 | 0 |
| 编译错误 | 0 | 0 | 0 |
| 测试失败 | 0 | 0 | 0 |

---

## ⚠️ 风险和问题

### 已知风险

1. **OpenAI API配额**
   - 影响: 高
   - 概率: 中
   - 缓解: 使用测试账户,限制QPS

2. **LLM响应解析**
   - 影响: 中
   - 概率: 中
   - 缓解: 使用StructuredOutputConverter

3. **性能达不到目标**
   - 影响: 中
   - 概率: 低
   - 缓解: 添加缓存,优化数据库查询

### 待解决问题

- [ ] OpenAI API Key配置
- [ ] ChatClient能否成功注入
- [ ] AuditRule表是否已创建
- [ ] 敏感词库初始化

---

## 📞 每日检查清单

### 周二 (2026-02-03)
- [ ] 环境检查完成
- [ ] DTO类完成
- [ ] AuditRuleFunctions初步完成
- [ ] CacheConfiguration完成
- [ ] 单元测试框架完成

### 周三 (2026-02-04)
- [ ] 测试用例完成
- [ ] 性能测试完成
- [ ] 代码审查完成
- [ ] PHASE_2_3_IMPLEMENTATION.md完成
- [ ] Phase 2.3 验收完成

### 周四 (2026-02-05)
- [ ] Prompt设计完成
- [ ] DTO类完成
- [ ] ChatClient配置完成
- [ ] ContentAuditAgent核心实现完成
- [ ] 初步编译验证通过

### 周五 (2026-02-06)
- [ ] 单元测试完成
- [ ] 集成测试完成
- [ ] 性能测试完成
- [ ] 文档编写完成
- [ ] 所有测试通过

### 周六 (2026-02-07)
- [ ] 最终代码审查完成
- [ ] 编译验证通过
- [ ] E2E验证通过
- [ ] 文档更新完成
- [ ] Git提交完成
- [ ] Phase 2.4 验收完成

---

## 📚 参考资源

### Phase 2.3 参考
- [PHASE_2_3_PLAN.md](./PHASE_2_3_PLAN.md) - 详细规划
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Caffeine缓存](https://github.com/ben-manes/caffeine)

### Phase 2.4 参考
- [PHASE_2_4_PLAN.md](./PHASE_2_4_PLAN.md) - 详细规划
- [Spring AI](https://docs.spring.io/spring-ai/reference/)
- [OpenAI API](https://platform.openai.com/docs/api-reference)

---

## 🎓 总结

**本周目标**:
- ✅ 完成Phase 2.3 (AuditRuleFunctions)
- ✅ 完成Phase 2.4 (ContentAuditAgent)
- ✅ 建立完整的审核智能体
- ✅ 所有代码可编译且测试通过

**成功标准**:
- 1,490+行代码交付
- 14-18个单元测试,100%通过
- ≥85%代码覆盖率
- 完整的文档

**下周计划**:
- Phase 2.5 (敏感词库优化)
- Phase 3.1 (REST API)

---

**文档创建时间**: 2026-01-27  
**周期**: 02-03 至 02-09  
**下次更新**: 2026-02-04 (周三)
