# Phase 2.2: CrawlerService 详细实现计划

**目标**: 实现爬虫服务，集成PlaywrightManager，实现完整的缓存三级检查和数据存储  
**工作量**: 5天  
**优先级**: 🔴 **高** (所有后续功能都依赖)

---

## 📋 任务分解和时间表

### 第1天: CrawlerService框架设计 (2.2.1 + 2.2.2 部分)
- [ ] 设计CrawlerService核心类结构
- [ ] 实现爬取帖子基本信息的选择器
- [ ] 测试Playwright导航和选择器稳定性

### 第2天: 爬虫核心逻辑 (2.2.2 完成)
- [ ] 实现getPostContent()方法
- [ ] 提取标题、正文、图片URL
- [ ] 提取Tag和其他元数据
- [ ] 异常处理和重试机制

### 第3天: 缓存和查询 (2.2.3 + 2.2.4)
- [ ] 实现三级缓存检查逻辑
- [ ] Redis缓存操作
- [ ] PostgreSQL查询
- [ ] 缓存回填和更新

### 第4天: 数据存储 (2.2.5)
- [ ] JSONB字段处理
- [ ] 事务管理
- [ ] 批量操作优化
- [ ] 性能基准测试

### 第5天: 单元测试 (2.2.6)
- [ ] 编写12个测试用例
- [ ] Mock Playwright依赖
- [ ] 集成测试验证缓存流程
- [ ] 代码审查和优化

---

## 🎯 实现步骤

### Step 1: 创建CrawlerService框架

**文件**: `src/main/java/com/xhs/audit/service/CrawlerService.java`

```java
@Service
@Slf4j
public class CrawlerService {
    
    @Autowired
    private PlaywrightManager playwrightManager;
    
    @Autowired
    private XhsContentRepository contentRepository;
    
    @Autowired
    private RedisTemplate<String, XhsContent> redisTemplate;
    
    private final String CONTENT_CACHE_PREFIX = "xhs:content:";
    private static final long CACHE_TTL_SECONDS = 86400; // 24小时
    
    // 核心方法: 爬取内容
    public XhsContent crawlContent(String url) throws Exception {
        // 三级缓存检查
        // 1. Redis检查
        // 2. PostgreSQL检查
        // 3. 执行爬虫
        // 4. 存储到Redis和PostgreSQL
        // 5. 返回结果
    }
    
    // 子方法: 获取帖子内容
    private XhsContent getPostContent(String url) throws Exception {
        // Playwright爬虫逻辑
    }
    
    // 子方法: 获取元数据
    private void enrichMetadata(XhsContent content, String url) throws Exception {
        // 提取发布者、时间、点赞数等
    }
}
```

### Step 2: 爬虫核心选择器

**小红书内容结构分析**:
```
帖子URL: https://www.xiaohongshu.com/discover/[postId]

需要爬取的数据:
- 标题: .describe__title 或 div[class*="title"]
- 正文: .desc__content 或 div[class*="content"]
- 图片: img[class*="image"]
- Tag: .tag 或 span[class*="tag"]
- 发布者: .author__name 或 span[class*="author"]
- 发布时间: .time-text 或 span[class*="time"]
- 点赞数: .feed-likes-count 或 span[class*="like"]
- 评论数: .comment-count 或 span[class*="comment"]
```

### Step 3: 缓存策略

**三级缓存检查流程**:
```
┌─────────────────────────────────────┐
│ 请求爬取内容(url)                    │
└──────────────┬──────────────────────┘
               │
        ┌──────▼──────┐
        │ Redis检查?  │
        │ (TTL:24h)   │
        └──────┬──────┘
               │YES   NO
               │        └─────┐
               │              │
         返回结果       ┌──────▼──────┐
                      │ PostgreSQL   │
                      │ 检查历史?    │
                      └──────┬──────┘
                            YES  NO
                             │    └──────┐
                             │           │
                        返回结果   ┌─────▼──────┐
                                 │ 执行爬虫    │
                                 │ (Playwright)│
                                 └─────┬──────┘
                                       │
                        ┌──────────────▼──────────────┐
                        │ 存储到Redis和PostgreSQL     │
                        │ Redis TTL: 24h             │
                        │ PostgreSQL: 永久           │
                        └──────────────┬──────────────┘
                                       │
                              返回爬取结果
```

### Step 4: 数据库操作优化

**JSONB字段最佳实践**:
```sql
-- content_data JSONB字段示例
{
  "title": "...",
  "description": "...",
  "images": ["url1", "url2"],
  "tags": ["tag1", "tag2"],
  "author": {
    "id": "xxx",
    "name": "xxx"
  },
  "stats": {
    "likes": 100,
    "comments": 50
  }
}

-- 查询优化
-- 创建GIN索引用于JSONB搜索
CREATE INDEX idx_content_data_gin ON xhs_content USING GIN(content_data);
```

### Step 5: 异常处理和重试

**异常分类**:
```
1. 网络异常 (可重试)
   - 连接超时
   - 读取超时
   
2. 页面解析异常 (可重试)
   - 选择器不匹配
   - 元素未加载
   
3. 业务异常 (不可重试)
   - 内容已删除
   - 无权限访问
```

**重试策略**:
```
- 最多重试3次
- 指数退避: 1s, 2s, 4s
- 只重试网络和解析异常
```

---

## 🧪 测试用例设计

### 12个测试用例

1. **缓存命中测试** (3个)
   - Redis缓存命中
   - PostgreSQL缓存命中
   - 都未命中时执行爬虫

2. **爬虫核心逻辑** (4个)
   - 成功爬取完整内容
   - 部分字段缺失处理
   - 图片URL提取
   - Tag提取

3. **缓存一致性** (2个)
   - Redis和PostgreSQL数据同步
   - 缓存过期更新

4. **异常处理** (2个)
   - 网络异常重试
   - 页面解析异常

5. **性能** (1个)
   - 缓存命中性能基准 < 10ms
   - 爬虫平均耗时 < 3s

---

## 📊 验收标准

- [ ] 代码覆盖率 >= 80%
- [ ] 所有12个测试用例通过
- [ ] 缓存命中率 > 75% (假定有20个相同URL的请求)
- [ ] 单次爬虫耗时 < 5s
- [ ] 缓存查询耗时 < 10ms
- [ ] 代码无SonarQube P1/P2问题
- [ ] Javadoc完整性 >= 95%

---

## 🔧 开发环境准备

```bash
# 1. 启动Docker容器
make docker-up

# 2. 运行数据库迁移
mvn flyway:migrate

# 3. 构建项目
make compile

# 4. 运行测试
make test-watch
```

---

## 📝 关键代码提交清单

1. **CrawlerService.java** (~250行)
   - 主要爬虫逻辑
   - 缓存检查
   - 数据库操作

2. **CrawlerServiceTest.java** (~300行)
   - 12个测试用例
   - Mock Playwright
   - 性能基准

3. **application.yml更新**
   - Redis连接配置优化
   - 缓存TTL配置

4. **数据库迁移脚本** (可选)
   - 创建JSONB索引
   - 性能优化

---

## ⏱️ 时间估计

| 任务 | 时间 | 优先级 |
|------|------|--------|
| 框架设计 | 2h | P0 |
| 爬虫逻辑 | 4h | P0 |
| 缓存集成 | 3h | P0 |
| 数据库操作 | 3h | P0 |
| 单元测试 | 5h | P0 |
| 代码审查和优化 | 2h | P1 |
| **总计** | **19小时** | |

**工作日估计**: 2.4天 (8小时/天)

---

## 🚀 下阶段准备 (Phase 2.3-2.4)

完成CrawlerService后，即可启动:
- **Phase 2.3**: AuditRuleFunctions (Function Calling工具)
- **Phase 2.4**: ContentAuditAgent (LLM审核引擎)

这两个Phase可以并行开发。

