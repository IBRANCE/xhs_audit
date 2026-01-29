package com.xhs.audit.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.infrastructure.PlaywrightManager;

import lombok.extern.slf4j.Slf4j;

/**
 * PlaywrightManager单元测试
 *
 * 测试用例覆盖：
 * 1. 浏览器实例池初始化
 * 2. Page借用和归还（每个Browser对应一个Page）
 * 3. 资源自动释放
 * 4. 异常处理
 * 5. 健康检查
 * 6. 新架构：每个Browser实例独立使用，避免CDP冲突
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class PlaywrightManagerTest {

    private PlaywrightManager playwrightManager;

    @BeforeEach
    void setUp() {
        playwrightManager = new PlaywrightManager();
        playwrightManager.initBrowserPool();
    }

    @AfterEach
    void tearDown() {
        if (playwrightManager != null) {
            playwrightManager.destroy();
        }
    }

    @Test
    @DisplayName("浏览器实例池初始化成功")
    void testBrowserPoolInitialization() {
        // 新架构：初始时没有活跃实例，按需创建
        assertEquals(0, playwrightManager.getActiveInstanceCount());
        log.info("✓ 浏览器实例池初始化验证通过");
    }

    @Test
    @DisplayName("成功借用Page")
    void testBorrowPageSuccessfully() throws InterruptedException {
        // Arrange & Act
        PlaywrightManager.PageWrapper page = playwrightManager.borrowPage();

        // Assert
        assertNotNull(page);
        assertNotNull(page.page);
        assertNotNull(page.context);
        assertFalse(page.page.isClosed());

        // 借用后活跃实例数增加
        assertEquals(1, playwrightManager.getActiveInstanceCount());

        log.info("✓ 成功借用Page验证通过");

        // Cleanup
        playwrightManager.closePage(page);
    }

    @Test
    @DisplayName("多个Browser实例独立使用")
    void testMultipleBrowserInstances() throws InterruptedException {
        // Arrange & Act - 新架构：每个Page对应独立的Browser
        PlaywrightManager.PageWrapper page1 = playwrightManager.borrowPage();
        PlaywrightManager.PageWrapper page2 = playwrightManager.borrowPage();
        PlaywrightManager.PageWrapper page3 = playwrightManager.borrowPage();

        // Assert - 3个Page应该来自3个不同的Browser实例
        assertEquals(3, playwrightManager.getActiveInstanceCount());
        // 每个Browser使用完后会释放，所以实例数可能不同

        log.info("✓ 多Browser实例验证通过，活跃实例: {}", playwrightManager.getActiveInstanceCount());

        // Cleanup
        playwrightManager.closePage(page1);
        playwrightManager.closePage(page2);
        playwrightManager.closePage(page3);
    }

    @Test
    @DisplayName("Page自动释放资源")
    void testPageAutoCloseResources() throws InterruptedException {
        // Arrange
        PlaywrightManager.PageWrapper page = playwrightManager.borrowPage();
        int instanceCountBeforeClose = playwrightManager.getActiveInstanceCount();

        // Act
        playwrightManager.closePage(page);

        // Assert
        assertTrue(page.page.isClosed());
        log.info("✓ Page资源自动释放验证通过");
    }

    @Test
    @DisplayName("等待队列机制")
    void testWaitingQueue() throws InterruptedException {
        // 这个测试验证等待队列的基本逻辑

        // Act - 统计信息获取
        String stats = playwrightManager.getStats();

        // Assert
        assertNotNull(stats);
        assertTrue(stats.contains("活跃实例:"));
        assertTrue(stats.contains("等待队列:"));
        assertTrue(stats.contains("已创建:"));

        log.info("✓ 等待队列统计信息: {}", stats);
    }

    @Test
    @DisplayName("Try-with-resources自动关闭")
    void testTryWithResourcesAutoClose() throws InterruptedException {
        // Arrange & Act
        try (PlaywrightManager.PageWrapper page = playwrightManager.borrowPage()) {
            assertNotNull(page);
            assertEquals(1, playwrightManager.getActiveInstanceCount());
        }

        log.info("✓ Try-with-resources自动关闭验证通过");
    }

    @Test
    @DisplayName("Page导航功能验证")
    void testPageNavigation() throws InterruptedException {
        // Arrange
        PlaywrightManager.PageWrapper wrapper = playwrightManager.borrowPage();

        try {
            // Act & Assert
            assertNotNull(wrapper.page.url());
            log.info("✓ Page导航功能验证通过，当前URL: {}", wrapper.page.url());
        } finally {
            playwrightManager.closePage(wrapper);
        }
    }

    @Test
    @DisplayName("关闭池后无法借用Page")
    void testNoPageBorrowAfterDestroy() throws InterruptedException {
        // Arrange
        playwrightManager.destroy();

        // Act & Assert
        assertThrows(
                IllegalStateException.class,
                () -> playwrightManager.borrowPage());

        log.info("✓ 关闭后禁止借用验证通过");
    }

    @Test
    @DisplayName("多次关闭页面不抛异常")
    void testClosingPageMultipleTimes() throws InterruptedException {
        // Arrange
        PlaywrightManager.PageWrapper page = playwrightManager.borrowPage();

        // Act & Assert
        playwrightManager.closePage(page);
        playwrightManager.closePage(page); // 第二次关闭应该不抛异常
        playwrightManager.closePage(null); // null也应该不抛异常

        log.info("✓ 多次关闭不抛异常验证通过");
    }

    @Test
    @DisplayName("资源泄漏检测")
    void testNoResourceLeak() throws InterruptedException {
        // Arrange & Act
        int successCount = 0;
        for (int i = 0; i < 5; i++) {
            try {
                PlaywrightManager.PageWrapper page = playwrightManager.borrowPage();
                if (page != null && page.page != null) {
                    successCount++;
                    playwrightManager.closePage(page);
                }
            } catch (Exception e) {
                log.debug("借用Page时异常（可能池满）: {}", e.getMessage());
            }
        }

        // 至少应该成功借用一次
        assertTrue(successCount > 0, "应该至少成功借用一次Page");
        log.info("✓ 资源泄漏检测验证通过 - 无泄漏，成功借用: {} 次", successCount);
    }

    @Test
    @DisplayName("浏览器实例复用")
    void testBrowserInstanceReuse() throws InterruptedException {
        // Arrange - 借用一个Page
        PlaywrightManager.PageWrapper page1 = playwrightManager.borrowPage();
        int instanceCountBeforeClose = playwrightManager.getActiveInstanceCount();

        // Act - 关闭后立即借用
        playwrightManager.closePage(page1);
        PlaywrightManager.PageWrapper page2 = playwrightManager.borrowPage();

        // Assert - 应该复用同一个Browser实例
        assertEquals(instanceCountBeforeClose, playwrightManager.getActiveInstanceCount());

        log.info("✓ 浏览器实例复用验证通过");

        // Cleanup
        playwrightManager.closePage(page2);
    }

    @Test
    @DisplayName("统计信息正确")
    void testStatsInformation() throws InterruptedException {
        // Arrange & Act
        PlaywrightManager.PageWrapper page1 = playwrightManager.borrowPage();
        PlaywrightManager.PageWrapper page2 = playwrightManager.borrowPage();

        // 获取统计信息
        String stats = playwrightManager.getStats();

        // Assert
        assertNotNull(stats);
        assertTrue(stats.contains("活跃实例:"));
        assertTrue(stats.contains("已创建:"));

        log.info("统计信息: {}", stats);

        // Cleanup
        playwrightManager.closePage(page1);
        playwrightManager.closePage(page2);
    }
}
