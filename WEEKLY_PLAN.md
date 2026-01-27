# 本周开发计划 (W5: 1/27 - 1/31)

**日期**: 2026年1月27日 - 1月31日  
**工作日**: 4天 (考虑周末)  
**目标**: Phase 2.2 CrawlerService 基础实现和测试

---

## 📊 每日计划

### 🗓️ 周一 (1/27) - 计划和准备

**上午**:
- [x] 完成 Phase 2.1 总结
- [x] 修复 Docker 启动脚本
- [x] 创建开发计划文档
- [x] 标记 TODO 和优先级

**下午**:
- [ ] 环境验证和测试
  - [ ] 验证 PlaywrightManager 编译
  - [ ] 测试 Docker 启动流程
  - [ ] 验证单元测试通过
- [ ] 准备开发环境

**完成标志**: 所有文件编译通过，Docker 容器运行正常

---

### 🗓️ 周二 (1/28) - CrawlerService 基础实现

**上午**:
- [ ] 创建 `service/CrawlerService.java` (约200行)
  - [ ] 类结构设计
  - [ ] 注入 PlaywrightManager
  - [ ] 定义核心方法签名
  
**下午**:
- [ ] 实现爬虫核心方法 (约150行)
  - [ ] `crawlPost(url)` - 单页爬取
  - [ ] `parsePostContent()` - 内容解析
  - [ ] 字段提取逻辑
    - 标题、正文、图片
    - 发布者、时间、点赞数

**完成标志**: 基础爬虫功能实现，可进行单页测试

**代码行数目标**: 约 350-400 行

---

### 🗓️ 周三 (1/29) - 缓存和存储层

**上午**:
- [ ] 缓存层集成 (约150行)
  - [ ] Redis 缓存检查
  - [ ] Caffeine 本地缓存
  - [ ] 缓存注解配置
  
**下午**:
- [ ] 数据库操作 (约100行)
  - [ ] 使用 XhsContentRepository 存储
  - [ ] 缓存失效处理
  - [ ] 缓存回填逻辑

**完成标志**: 三级缓存完全集成，可进行缓存测试

**代码行数目标**: 约 250-300 行

---

### 🗓️ 周四 (1/30) - 错误处理和测试

**上午**:
- [ ] 错误处理和重试 (约100行)
  - [ ] 3次重试机制
  - [ ] 异常捕获
  - [ ] 日志记录
  
**下午**:
- [ ] 单元测试编写 (约300-400行)
  - [ ] CrawlerServiceTest 创建
  - [ ] 12+ 个测试用例
    - [ ] 单页爬取测试
    - [ ] 缓存命中测试
    - [ ] 错误重试测试
    - [ ] 并发测试

**完成标志**: 单元测试通过率 100%，覆盖率 ≥80%

**代码行数目标**: 测试代码 300-400 行

---

### 🗓️ 周五 (1/31) - 集成和文档

**上午**:
- [ ] 集成测试 (如需要，约100行)
  - [ ] 与 PlaywrightManager 集成验证
  - [ ] 完整流程测试

**下午**:
- [ ] 代码审查和优化
  - [ ] 代码规范检查
  - [ ] 性能优化建议
  - [ ] 文档同步更新
- [ ] Phase 2.2 总结
  - [ ] 性能指标记录
  - [ ] 问题和解决方案
  - [ ] Phase 2.3 准备

**完成标志**: CrawlerService 完全实现和测试，准备进入 Phase 2.3

---

## 📈 技术指标目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 代码行数 | 900-1200 | 核心逻辑 + 测试 |
| 单元测试覆盖率 | ≥85% | CrawlerService |
| 编译成功率 | 100% | 无警告 |
| 单页爬取延迟 | <5s (P99) | 包含重试 |
| 缓存命中率 | >70% | Redis + Caffeine |
| 并发吞吐 | 100条/分钟 | 3 Browser 实例 |

---

## ✅ 每日检查清单

### 编译检查
```bash
mvn clean compile
```

### 测试检查
```bash
mvn test
```

### 提交前检查
```bash
# 1. 编译
mvn clean compile

# 2. 单元测试
mvn test

# 3. 代码覆盖率
mvn jacoco:report

# 4. 格式检查
# (可选) mvn formatter:validate
```

---

## 📋 任务分解细节

### CrawlerService 架构

```
CrawlerService
├── playlistManager: PlaywrightManager  (注入)
├── xhsContentRepository: XhsContentRepository  (注入)
├── redisTemplate: RedisTemplate  (注入)
├── caffeineCache: Cache  (自动配置)
│
├── crawlPost(url)  ← 单页爬取入口
│   ├── 1. Redis 缓存检查
│   ├── 2. 如未命中，Caffeine 检查
│   ├── 3. 都未命中，执行爬虫
│   │   ├── borrowPage()
│   │   ├── navigate(url)
│   │   ├── parseContent()
│   │   └── closePage()
│   ├── 4. 缓存存储
│   └── 5. 返回 XhsContent
│
├── crawlPosts(urls)  ← 批量爬取
│   ├── 遍历列表
│   ├── 调用 crawlPost()
│   ├── 累计结果
│   └── 返回列表
│
├── parseContent()  ← 内容解析
│   ├── 提取标题
│   ├── 提取图片 URL
│   ├── 提取正文
│   ├── 提取 Tag
│   ├── 提取发布者信息
│   └── 返回 XhsContent
│
└── handleError()  ← 错误处理
    ├── 记录日志
    ├── 重试计数
    └── 抛出异常
```

---

## 🔧 技术决策

### 缓存策略
```
查询流程：
1. Redis (TTL: 24小时)
   ↓ (未命中)
2. Caffeine (TTL: 10分钟)
   ↓ (未命中)
3. 执行爬虫
   ↓
4. 存储到 Redis
5. 存储到 Caffeine
6. 存储到 PostgreSQL
```

### 错误处理
```
错误流程：
网络错误或超时
   ↓
重试 (第1次)
   ↓
重试 (第2次)
   ↓
重试 (第3次)
   ↓
记录失败
   ↓
抛出 CrawlerException
```

---

## 📚 参考代码结构

### 方法签名示例

```java
@Service
@Slf4j
public class CrawlerService {
    
    private final PlaywrightManager playwrightManager;
    private final XhsContentRepository xhsContentRepository;
    private final RedisTemplate<String, XhsContent> redisTemplate;
    private final Cache caffeineCache;
    
    /**
     * 爬取单个帖子
     * @param url 小红书帖子URL
     * @return XhsContent 内容对象
     * @throws CrawlerException 爬取失败
     */
    public XhsContent crawlPost(String url) { ... }
    
    /**
     * 批量爬取
     * @param urls URL列表
     * @return XhsContent 列表
     */
    public List<XhsContent> crawlPosts(List<String> urls) { ... }
    
    /**
     * 解析内容
     */
    private XhsContent parseContent(Page page) { ... }
}
```

---

## 🧪 测试用例示例

```java
class CrawlerServiceTest {
    
    // 测试1: 单页爬取成功
    void testCrawlPostSuccess()
    
    // 测试2: 缓存命中 (Redis)
    void testCrawlPostWithRedisCache()
    
    // 测试3: 缓存命中 (Caffeine)
    void testCrawlPostWithCaffeineCache()
    
    // 测试4: 缓存失效，重新爬取
    void testCrawlPostCacheExpired()
    
    // 测试5: 错误重试成功
    void testCrawlPostRetrySuccess()
    
    // 测试6: 重试失败
    void testCrawlPostRetryFailed()
    
    // 测试7: 并发爬取
    void testConcurrentCrawling()
    
    // 测试8: 批量爬取
    void testBatchCrawlPosts()
    
    // 测试9: 字段提取完整性
    void testContentParsing()
    
    // 测试10: 内容验证
    void testContentValidation()
}
```

---

## 🎯 完成条件

### Phase 2.2 完成标志

- [x] CrawlerService 完全实现
- [ ] 单元测试覆盖率 ≥85%
- [ ] 所有测试通过
- [ ] 与 PlaywrightManager 集成成功
- [ ] 代码审查通过
- [ ] 文档完整更新
- [ ] 性能测试通过
- [ ] 无代码警告

---

## 📞 遇到问题时

### Docker 相关
```bash
# 重启 PostgreSQL
docker-compose restart postgres

# 重启 Redis
docker-compose restart redis

# 查看日志
docker-compose logs -f postgres
```

### 代码相关
- 查看 DEBUG.md 的常见问题部分
- 查看 QUICKSTART.md
- 参考 PHASE_2_1_SUMMARY.md 中的架构说明

---

## 提交规范

### Commit Message 格式

```
feat(crawler): 实现爬虫核心方法

- 实现 crawlPost() 单页爬取
- 实现内容字段提取
- 添加错误处理和重试机制

Related to #2.2
```

### 提交检查清单

```
[ ] 代码编译通过 (mvn clean compile)
[ ] 单元测试通过 (mvn test)
[ ] 没有新的警告信息
[ ] 文档已更新
[ ] Commit message 清晰
```

---

**预计总工作量**: 32-40 小时  
**预计完成日期**: 2026-02-01 (周六/周一)  
**下一阶段**: Phase 2.3 AuditRuleFunctions (2/3 开始)

**最后更新**: 2026-01-27 14:45:00
