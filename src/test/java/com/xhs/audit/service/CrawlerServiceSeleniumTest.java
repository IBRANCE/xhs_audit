package com.xhs.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.model.entity.XhsContent;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Selenium Grid 的爬虫服务测试
 * 
 * 运行前需要：
 * 1. 启动 Selenium Grid: ./scripts/start-selenium-grid.sh
 * 2. 设置环境变量: export SELENIUM_GRID_ENABLED=true
 * 3. 运行测试: mvn test -Dtest=CrawlerServiceSeleniumTest
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@EnabledIfEnvironmentVariable(named = "SELENIUM_GRID_ENABLED", matches = "true")
public class CrawlerServiceSeleniumTest {

    @Autowired
    private CrawlerService crawlerService;

    private static final String TEST_URL = "https://www.xiaohongshu.com/explore/676c8b2c000000001e009b40";

    @BeforeEach
    public void setup() {
        log.info("=".repeat(60));
        log.info("开始 Selenium Grid 爬虫测试");
        log.info("=".repeat(60));
    }

    @Test
    public void testCrawlContent() throws Exception {
        log.info("测试URL: {}", TEST_URL);

        long startTime = System.currentTimeMillis();

        // 执行爬取
        XhsContent content = crawlerService.crawlContent(TEST_URL);

        long duration = System.currentTimeMillis() - startTime;

        // 验证结果
        assertNotNull(content, "爬取内容不应为null");
        assertNotNull(content.getPostId(), "PostId不应为null");
        assertNotNull(content.getUrl(), "URL不应为null");

        log.info("=".repeat(60));
        log.info("爬取成功！");
        log.info("PostId: {}", content.getPostId());
        log.info("标题: {}", content.getTitle());
        log.info("正文长度: {}", content.getContent() != null ? content.getContent().length() : 0);
        log.info("图片数量: {}", content.getImages() != null ? content.getImages().size() : 0);
        log.info("标签数量: {}", content.getTags() != null ? content.getTags().size() : 0);
        log.info("耗时: {} ms", duration);
        log.info("=".repeat(60));

        // 验证至少有标题或内容
        assertTrue(
                (content.getTitle() != null && !content.getTitle().isEmpty()) ||
                        (content.getContent() != null && !content.getContent().isEmpty()),
                "应该至少有标题或内容");
    }

    @Test
    public void testCrawlContentBatch() {
        log.info("测试批量爬取（并发）");

        // 准备测试URL列表（使用相同URL多次，测试并发能力）
        java.util.List<String> urls = java.util.List.of(
                TEST_URL,
                TEST_URL,
                TEST_URL);

        long startTime = System.currentTimeMillis();

        // 批量爬取（内部使用并行流，会利用 Selenium Grid 的多节点）
        java.util.List<XhsContent> results = crawlerService.crawlContentBatch(urls);

        long duration = System.currentTimeMillis() - startTime;

        log.info("=".repeat(60));
        log.info("批量爬取完成！");
        log.info("任务数量: {}", urls.size());
        log.info("成功数量: {}", results.size());
        log.info("总耗时: {} ms", duration);
        log.info("平均耗时: {} ms/个", duration / urls.size());
        log.info("=".repeat(60));

        // 验证结果
        assertFalse(results.isEmpty(), "批量爬取应该有结果");
        assertEquals(urls.size(), results.size(), "成功数量应该等于任务数量");
    }

    @Test
    public void testInvalidUrl() {
        log.info("测试无效URL");

        String invalidUrl = "https://www.xiaohongshu.com/invalid/url";

        Exception exception = assertThrows(Exception.class, () -> {
            crawlerService.crawlContent(invalidUrl);
        });

        log.info("预期的异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("无效的小红书URL"));
    }
}
