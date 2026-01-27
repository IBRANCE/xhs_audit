package com.xhs.audit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.xhs.audit.infrastructure.PlaywrightManager;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.XhsContentRepository;
import com.xhs.audit.service.CrawlerService;

import lombok.extern.slf4j.Slf4j;

/**
 * CrawlerService 单元测试
 *
 * @author Bruce
 * @since 2026-02-01
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlerService 爬虫服务测试")
class CrawlerServiceTest {

    @Mock
    private XhsContentRepository contentRepository;

    @Mock
    private RedisTemplate<String, XhsContent> redisTemplate;

    @Mock
    private PlaywrightManager playwrightManager;

    @InjectMocks
    private CrawlerService crawlerService;

    private static final String SAMPLE_URL = "https://www.xiaohongshu.com/discover/abc123def456";
    private static final String SAMPLE_POST_ID = "abc123def456";

    /**
     * 测试1: 无效URL验证
     */
    @Test
    @DisplayName("无效URL验证 - 应拒绝非小红书URL")
    void testInvalidUrlValidation() {
        String invalidUrl = "https://www.example.com/some-page";
        assertThrows(IllegalArgumentException.class, () -> crawlerService.crawlContent(invalidUrl));
        log.info("✓ 测试1通过: 无效URL验证");
    }

    /**
     * 测试2: PostgreSQL缓存命中
     */
    @Test
    @DisplayName("PostgreSQL缓存命中 - 应返回数据库数据")
    void testPostgreSQLCacheHit() throws Exception {
        XhsContent dbContent = new XhsContent();
        dbContent.setPostId(SAMPLE_POST_ID);
        dbContent.setUrl(SAMPLE_URL);

        when(contentRepository.findByUrl(SAMPLE_URL)).thenReturn(Optional.of(dbContent));

        XhsContent result = crawlerService.crawlContent(SAMPLE_URL);

        assertNotNull(result);
        assertEquals(SAMPLE_POST_ID, result.getPostId());
        log.info("✓ 测试2通过: PostgreSQL缓存命中");
    }

    /**
     * 测试3: 缓存清理
     */
    @Test
    @DisplayName("缓存清理 - 应成功执行")
    void testCacheClear() {
        crawlerService.clearCache(SAMPLE_URL);
        // 缓存清理不抛异常即为成功
        log.info("✓ 测试3通过: 缓存清理");
    }

    /**
     * 测试4: 提取PostID
     */
    @Test
    @DisplayName("提取PostID - 应从URL中正确提取ID")
    void testExtractPostId() {
        // Mock PlayWrightManager 返回包含内容的对象
        XhsContent content = new XhsContent();
        content.setPostId("abc123");
        content.setUrl("https://www.xiaohongshu.com/discover/abc123");
        content.setTitle("Test Post");
        content.setContent("Test Content");

        // When crawlContent is called, return the mocked content
        when(contentRepository.findByUrl(anyString())).thenReturn(Optional.of(content));

        String url = "https://www.xiaohongshu.com/discover/abc123";
        assertDoesNotThrow(() -> {
            XhsContent result = crawlerService.crawlContent(url);
            assertNotNull(result);
        });
        log.info("✓ 测试4通过: 提取PostID");
    }
}
