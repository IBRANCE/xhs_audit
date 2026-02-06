package com.xhs.audit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.xhs.audit.infrastructure.SeleniumManager;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.XhsContentRepository;

/**
 * CrawlerService 单元测试
 *
 * 测试爬虫服务的核心功能：
 * 1. URL 验证逻辑
 * 2. 三级缓存机制 (Redis -> PostgreSQL -> 缓存)
 * 3. 异常处理
 * 4. 批量爬取
 *
 * 注意：完整的 Selenium WebDriver 测试需要集成测试环境
 *
 * @author XHS Audit System
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrawlerServiceTest {

    private CrawlerService crawlerService;

    @Mock
    private SeleniumManager seleniumManager;

    @Mock
    private XhsContentRepository contentRepository;

    @Mock
    private RedisTemplate<String, XhsContent> redisTemplate;

    @Mock
    private ValueOperations<String, XhsContent> valueOperations;

    private static final String VALID_XHS_URL = "https://www.xiaohongshu.com/explore/abc123def456";
    private static final String VALID_SHORTLINK_URL = "https://xhslink.com/o/abc123";
    private static final String INVALID_URL = "https://www.example.com/page";

    @BeforeEach
    void setUp() throws Exception {
        // 创建 CrawlerService 实例并使用反射设置依赖
        crawlerService = new CrawlerService();
        setField(crawlerService, "seleniumManager", seleniumManager);
        setField(crawlerService, "contentRepository", contentRepository);
        setField(crawlerService, "redisTemplate", redisTemplate);

        // 初始化 RedisTemplate mock
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("URL 验证测试")
    class UrlValidationTests {

        @Test
        @DisplayName("无效 URL 应该抛出 IllegalArgumentException")
        void testInvalidUrl() {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                crawlerService.crawlContent(INVALID_URL);
            });

            assertTrue(exception.getMessage().contains("无效的小红书URL"));
        }

        @Test
        @DisplayName("null URL 应该抛出异常")
        void testNullUrl() {
            assertThrows(NullPointerException.class, () -> {
                crawlerService.crawlContent(null);
            });
        }

        @Test
        @DisplayName("空字符串 URL 应该抛出异常")
        void testEmptyUrl() {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                crawlerService.crawlContent("");
            });
        }
    }

    @Nested
    @DisplayName("Redis 缓存测试")
    class RedisCacheTests {

        @Test
        @DisplayName("Redis 缓存命中时应该直接返回缓存内容")
        void testRedisCacheHit() throws Exception {
            String postId = "abc123def456";
            String cacheKey = "xhs:content:" + postId;
            XhsContent cachedContent = createMockContent(postId, VALID_XHS_URL);

            when(valueOperations.get(cacheKey)).thenReturn(cachedContent);

            XhsContent result = crawlerService.crawlContent(VALID_XHS_URL);

            assertNotNull(result);
            assertEquals(postId, result.getPostId());
            verify(contentRepository, never()).findByUrl(anyString());
            verify(seleniumManager, never()).borrowDriver();
        }

        @Test
        @DisplayName("Redis 缓存未命中时应该查询数据库")
        void testRedisCacheMiss() throws Exception {
            String postId = "abc123def456";
            String cacheKey = "xhs:content:" + postId;
            XhsContent dbContent = createMockContent(postId, VALID_XHS_URL);

            when(valueOperations.get(cacheKey)).thenReturn(null); // Redis 缓存未命中
            when(contentRepository.findByUrl(VALID_XHS_URL)).thenReturn(Optional.of(dbContent)); // 数据库命中

            XhsContent result = crawlerService.crawlContent(VALID_XHS_URL);

            assertNotNull(result);
            assertEquals(postId, result.getPostId());
        }
    }

    @Nested
    @DisplayName("数据库缓存测试")
    class DatabaseCacheTests {

        @Test
        @DisplayName("数据库缓存命中且有效时应该返回内容并更新 Redis")
        void testDatabaseCacheHit() throws Exception {
            String postId = "abc123def456";
            XhsContent dbContent = createMockContent(postId, VALID_XHS_URL);

            when(valueOperations.get(anyString())).thenReturn(null); // Redis 缓存未命中
            when(contentRepository.findByUrl(VALID_XHS_URL)).thenReturn(Optional.of(dbContent)); // 数据库命中

            XhsContent result = crawlerService.crawlContent(VALID_XHS_URL);

            assertNotNull(result);
            assertEquals(postId, result.getPostId());
            verify(valueOperations).set(anyString(), any(XhsContent.class), anyLong(), any());
        }

        @Test
        @DisplayName("数据库和 Redis 缓存都未命中时应该尝试爬取")
        void testBothCacheMiss() throws Exception {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(contentRepository.findByUrl(VALID_XHS_URL)).thenReturn(Optional.empty());
            when(seleniumManager.borrowDriver()).thenThrow(
                new SeleniumManager.BrowserPoolExhaustedException("浏览器池耗尽"));

            // 由于有重试机制，预期会抛出 RuntimeException
            Exception exception = assertThrows(RuntimeException.class, () -> {
                crawlerService.crawlContent(VALID_XHS_URL);
            });

            // 验证重试后仍然失败
            assertTrue(exception.getMessage().contains("爬虫失败") || exception.getMessage().contains("浏览器池"));
            verify(seleniumManager, atLeast(1)).borrowDriver();
        }
    }

    @Nested
    @DisplayName("Web 爬取异常测试")
    class WebScrapingTests {

        @Test
        @DisplayName("浏览器池耗尽时应该抛出异常")
        void testBrowserPoolExhausted() throws Exception {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(contentRepository.findByUrl(VALID_XHS_URL)).thenReturn(Optional.empty());

            when(seleniumManager.borrowDriver()).thenThrow(
                new SeleniumManager.BrowserPoolExhaustedException("浏览器池暂时耗尽"));

            Exception exception = assertThrows(RuntimeException.class, () -> {
                crawlerService.crawlContent(VALID_XHS_URL);
            });

            assertTrue(exception.getMessage().contains("爬虫失败"));
        }
    }

    @Nested
    @DisplayName("批量爬取测试")
    class BatchCrawlTests {

        @Test
        @DisplayName("批量爬取应该处理所有 URL")
        void testBatchCrawl() throws Exception {
            List<String> urls = List.of(
                "https://www.xiaohongshu.com/explore/abc123",
                "https://www.xiaohongshu.com/explore/def456",
                "https://www.xiaohongshu.com/explore/ghi789"
            );

            // 设置所有 URL 都有 Redis 缓存命中，避免实际爬取
            when(valueOperations.get(anyString())).thenAnswer(invocation -> {
                String cacheKey = invocation.getArgument(0);
                String postId = cacheKey.replace("xhs:content:", "");
                return createMockContent(postId, "https://www.xiaohongshu.com/explore/" + postId);
            });

            List<XhsContent> results = crawlerService.crawlContentBatch(urls);

            assertEquals(3, results.size());
        }
    }

    @Nested
    @DisplayName("缓存清理测试")
    class CacheClearTests {

        @Test
        @DisplayName("清理缓存应该删除 Redis 中的缓存")
        void testClearCache() {
            String postId = "abc123def456";
            when(redisTemplate.delete("xhs:content:" + postId)).thenReturn(true);

            crawlerService.clearCache(VALID_XHS_URL);

            verify(redisTemplate).delete("xhs:content:" + postId);
        }

        @Test
        @DisplayName("清理缓存时 Redis 为 null 不应该报错")
        void testClearCacheWithNullRedis() throws Exception {
            // 设置 redisTemplate 为 null
            setField(crawlerService, "redisTemplate", null);

            // 不应该抛出异常
            assertDoesNotThrow(() -> {
                crawlerService.clearCache(VALID_XHS_URL);
            });
        }
    }

    // ===== Helper Methods =====

    private XhsContent createMockContent(String postId, String url) {
        XhsContent content = new XhsContent();
        content.setId(1L);
        content.setPostId(postId);
        content.setUrl(url);
        content.setTitle("测试标题");
        content.setContent("测试正文内容");
        content.setImages(List.of("https://example.com/image1.jpg"));
        content.setTags(List.of("测试标签1", "测试标签2"));
        content.setMetadata(Map.of("likes", 100, "comments", 50));
        content.setCrawledAt(LocalDateTime.now());
        content.setCreatedAt(LocalDateTime.now());
        content.setUpdatedAt(LocalDateTime.now());
        return content;
    }
}
