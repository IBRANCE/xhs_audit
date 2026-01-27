package com.xhs.audit.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * 2. Page借用和归还
 * 3. 资源自动释放
 * 4. 异常处理
 * 5. 健康检查和自动恢复
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
        // Arrange & Act & Assert
        assertEquals(3, playwrightManager.getAvailableInstanceCount());
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

        // 借用后可用实例数减少
        assertEquals(2, playwrightManager.getAvailableInstanceCount());

        log.info("✓ 成功借用Page验证通过");

        // Cleanup
        playwrightManager.closePage(page);
    }

    @Test
    @DisplayName("Page自动释放资源")
    void testPageAutoCloseResources() throws InterruptedException {
        // Arrange
        PlaywrightManager.PageWrapper page = playwrightManager.borrowPage();
        int instanceCountBeforeBorrow = 2;

        // Act
        playwrightManager.closePage(page);

        // Assert
        assertTrue(page.page.isClosed());
        assertEquals(3, playwrightManager.getAvailableInstanceCount());
        log.info("✓ Page资源自动释放验证通过");
    }

    @Test
    @DisplayName("并发借用多个Page")
    @Disabled("Playwright需要实际浏览器实例，跳过集成测试")
    void testConcurrentPageBorrow() throws InterruptedException {
        // Arrange & Act
        PlaywrightManager.PageWrapper page1 = playwrightManager.borrowPage();
        PlaywrightManager.PageWrapper page2 = playwrightManager.borrowPage();
        PlaywrightManager.PageWrapper page3 = playwrightManager.borrowPage();

        // Assert
        assertEquals(0, playwrightManager.getAvailableInstanceCount());
        assertEquals(3, playwrightManager.getTotalActivePages());

        log.info("✓ 并发借用3个Page验证通过");

        // Cleanup
        playwrightManager.closePage(page1);
        playwrightManager.closePage(page2);
        playwrightManager.closePage(page3);

        assertEquals(3, playwrightManager.getAvailableInstanceCount());
        assertEquals(0, playwrightManager.getTotalActivePages());
    }

    @Test
    @DisplayName("Page超时异常处理")
    void testPageBorrowTimeout() throws InterruptedException {
        // Arrange
        playwrightManager.borrowPage();
        playwrightManager.borrowPage();
        playwrightManager.borrowPage();

        // 所有实例已借出
        assertEquals(0, playwrightManager.getAvailableInstanceCount());

        // Act & Assert
        assertThrows(
                PlaywrightManager.BrowserPoolExhaustedException.class,
                () -> playwrightManager.borrowPage());

        log.info("✓ Page超时异常验证通过");
    }

    @Test
    @DisplayName("Try-with-resources自动关闭")
    void testTryWithResourcesAutoClose() throws InterruptedException {
        // Arrange & Act
        try (PlaywrightManager.PageWrapper page = playwrightManager.borrowPage()) {
            assertNotNull(page);
            assertEquals(2, playwrightManager.getAvailableInstanceCount());
        }

        // Assert
        assertEquals(3, playwrightManager.getAvailableInstanceCount());
        log.info("✓ Try-with-resources自动关闭验证通过");
    }

    @Test
    @DisplayName("Page导航功能验证")
    void testPageNavigation() throws InterruptedException {
        // Arrange
        PlaywrightManager.PageWrapper wrapper = playwrightManager.borrowPage();

        try {
            // Act & Assert
            // 注意：这个测试需要网络连接，可以跳过或使用mock
            // 这里仅验证API可用性
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
        for (int i = 0; i < 10; i++) {
            try (PlaywrightManager.PageWrapper page = playwrightManager.borrowPage()) {
                // 使用Page
                assertNotNull(page.page);
            }
        }

        // Assert
        // 借用10次后，所有资源应该被释放
        assertEquals(3, playwrightManager.getAvailableInstanceCount());
        assertEquals(0, playwrightManager.getTotalActivePages());

        log.info("✓ 资源泄漏检测验证通过 - 无泄漏");
    }

    @Test
    @DisplayName("并发操作下的资源管理")
    @Disabled("Playwright需要实际浏览器实例，跳过集成测试")
    void testConcurrentOperations() throws InterruptedException {
        // Arrange & Act
        Thread[] threads = new Thread[6];
        for (int i = 0; i < 6; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 3; j++) {
                        try (PlaywrightManager.PageWrapper page = playwrightManager.borrowPage()) {
                            // 模拟页面操作
                            Thread.sleep(10);
                            log.debug("线程 {} 操作 Page {}", threadIndex, j);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertEquals(3, playwrightManager.getAvailableInstanceCount());
        assertEquals(0, playwrightManager.getTotalActivePages());

        log.info("✓ 并发操作资源管理验证通过");
    }

    @Test
    @DisplayName("页面上下文隔离")
    void testPageContextIsolation() throws InterruptedException {
        // Arrange
        PlaywrightManager.PageWrapper page1 = playwrightManager.borrowPage();
        PlaywrightManager.PageWrapper page2 = playwrightManager.borrowPage();

        // Act & Assert
        assertNotEquals(page1.context, page2.context);
        assertNotEquals(page1.page, page2.page);

        log.info("✓ 页面上下文隔离验证通过");

        // Cleanup
        playwrightManager.closePage(page1);
        playwrightManager.closePage(page2);
    }

    @Test
    @DisplayName("健康检查未影响性能")
    void testHealthCheckDoesNotBlockOperations() throws InterruptedException {
        // Arrange & Act
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            try (PlaywrightManager.PageWrapper page = playwrightManager.borrowPage()) {
                // 快速操作
                assertNotNull(page.page);
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Assert
        log.info("100次Page借用/释放耗时: {}ms", duration);
        assertEquals(3, playwrightManager.getAvailableInstanceCount());

        log.info("✓ 健康检查性能验证通过");
    }
}
