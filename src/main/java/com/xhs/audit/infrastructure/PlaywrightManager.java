package com.xhs.audit.infrastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.*;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Playwright浏览器实例池管理器
 * 
 * 职责：
 * 1. 维护3个浏览器实例的生命周期
 * 2. 通过BlockingQueue管理实例分配
 * 3. 定期健康检查和自动恢复
 * 4. 优雅关闭时释放所有资源
 * 
 * 使用示例：
 * 
 * <pre>
 * try {
 *     PlaywrightManager.PageWrapper page = manager.borrowPage();
 *     page.page.navigate("https://example.com");
 *     // 爬虫操作
 * } finally {
 *     manager.closePage(page);
 * }
 * </pre>
 */
@Slf4j
@Component
public class PlaywrightManager implements DisposableBean {

    private static final int POOL_SIZE = 3;
    private static final long BORROW_TIMEOUT_SECONDS = 5;
    private static final int MAX_RETRIES = 3;
    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 30;
    private static final int MAX_PAGES_PER_BROWSER = 10;
    private static final int MAX_FAILURES_THRESHOLD = 3;

    private BlockingQueue<BrowserInstance> browserPool;
    private ScheduledExecutorService healthChecker;
    private Playwright playwright;
    private volatile boolean isShuttingDown = false;

    /**
     * 浏览器实例包装类
     * 跟踪浏览器状态和活跃Page
     */
    static class BrowserInstance {
        Browser browser;
        final AtomicInteger failureCount = new AtomicInteger(0);
        long lastUsedTime;
        final Set<String> activePageIds = ConcurrentHashMap.newKeySet();

        /**
         * 检查浏览器实例是否健康
         */
        boolean isHealthy() {
            try {
                // 检查浏览器连接
                browser.contexts().size();

                // 检查失败计数和活跃Page数
                return failureCount.get() < MAX_FAILURES_THRESHOLD
                        && activePageIds.size() < MAX_PAGES_PER_BROWSER;
            } catch (Exception e) {
                failureCount.incrementAndGet();
                return false;
            }
        }

        /**
         * 关闭浏览器实例及其所有资源
         * 三级资源释放：Page → Context → Browser → Playwright
         */
        void close() {
            try {
                // 1. 关闭所有活跃的Page
                try {
                    for (Page page : browser.contexts().stream()
                            .flatMap(ctx -> ctx.pages().stream())
                            .collect(Collectors.toList())) {
                        try {
                            if (!page.isClosed()) {
                                page.close();
                            }
                        } catch (Exception e) {
                            log.warn("关闭Page失败", e);
                        }
                    }
                } catch (Exception e) {
                    log.warn("枚举Page列表失败", e);
                }

                // 2. 关闭所有Context
                try {
                    for (BrowserContext ctx : new ArrayList<>(browser.contexts())) {
                        try {
                            ctx.close();
                        } catch (Exception e) {
                            log.warn("关闭BrowserContext失败", e);
                        }
                    }
                } catch (Exception e) {
                    log.warn("枚举Context列表失败", e);
                }

                // 3. 关闭浏览器
                try {
                    browser.close();
                } catch (Exception e) {
                    log.warn("关闭Browser失败", e);
                }
            } catch (Exception e) {
                log.error("关闭浏览器实例失败", e);
            }
        }
    }

    /**
     * Page包装类，提供try-with-resources支持
     */
    public static class PageWrapper implements AutoCloseable {
        public final Page page;
        public final BrowserContext context;
        private final BrowserInstance instance;
        private final String pageId;
        private final PlaywrightManager manager;

        public PageWrapper(Page page, BrowserContext context,
                BrowserInstance instance, String pageId,
                PlaywrightManager manager) {
            this.page = page;
            this.context = context;
            this.instance = instance;
            this.pageId = pageId;
            this.manager = manager;
        }

        @Override
        public void close() {
            manager.closePage(this);
        }
    }

    /**
     * 初始化浏览器实例池
     * 
     * @PostConstruct会在Spring容器初始化后自动调用
     */
    @PostConstruct
    public void initBrowserPool() {
        log.info("正在初始化浏览器实例池，目标大小: {}", POOL_SIZE);

        try {
            // 创建Playwright实例
            playwright = Playwright.create();
            browserPool = new LinkedBlockingQueue<>(POOL_SIZE);

            // 创建浏览器实例
            for (int i = 0; i < POOL_SIZE; i++) {
                try {
                    BrowserInstance instance = createBrowserInstance(i);
                    browserPool.offer(instance);
                    log.info("浏览器实例 {} 创建成功", i + 1);
                } catch (Exception e) {
                    log.error("创建浏览器实例 {} 失败", i + 1, e);
                }
            }

            // 启动健康检查线程
            scheduleHealthCheck();

            log.info("浏览器实例池初始化完成: {} 个实例", browserPool.size());
        } catch (Exception e) {
            log.error("浏览器实例池初始化失败", e);
            throw new RuntimeException("PlaywrightManager初始化失败", e);
        }
    }

    /**
     * 创建单个浏览器实例
     */
    private BrowserInstance createBrowserInstance(int index) {
        BrowserInstance instance = new BrowserInstance();

        // 启动Chromium浏览器
        instance.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(true) // 无头模式
                        .setArgs(Arrays.asList(
                                "--disable-blink-features=AutomationControlled",
                                "--no-sandbox",
                                "--disable-gpu")));

        instance.lastUsedTime = System.currentTimeMillis();
        instance.failureCount.set(0);

        log.debug("创建浏览器实例 {}: {}", index, instance.browser);
        return instance;
    }

    /**
     * 从池中借用Page
     * 
     * @return PageWrapper 页面包装器，使用完后必须关闭
     * @throws InterruptedException          如果等待被中断
     * @throws BrowserPoolExhaustedException 如果池中没有可用实例
     */
    public PageWrapper borrowPage() throws InterruptedException {
        if (isShuttingDown) {
            throw new IllegalStateException("浏览器池正在关闭，无法借用新Page");
        }

        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            BrowserInstance instance = browserPool.poll(
                    BORROW_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);

            if (instance == null) {
                log.warn("浏览器池超时，重试 {}/{}", retry + 1, MAX_RETRIES);
                continue;
            }

            if (!instance.isHealthy()) {
                log.warn("检测到不健康的浏览器实例，正在重建");
                instance.close();
                try {
                    instance = createBrowserInstance(retry);
                } catch (Exception e) {
                    log.error("重建浏览器实例失败", e);
                    continue;
                }
            }

            try {
                // 创建新的BrowserContext（模拟iPhone 13 Pro）
                BrowserContext context = instance.browser.newContext(
                        new Browser.NewContextOptions()
                                .setUserAgent(
                                        "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) " +
                                                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1")
                                .setViewportSize(390, 844) // iPhone 13 Pro
                                .setLocale("zh-CN"));

                // 创建新的Page
                Page page = context.newPage();
                String pageId = UUID.randomUUID().toString();
                instance.activePageIds.add(pageId);
                instance.lastUsedTime = System.currentTimeMillis();

                log.debug("成功借用Page: {}", pageId);
                return new PageWrapper(page, context, instance, pageId, this);

            } catch (Exception e) {
                log.error("创建Page失败，重试 {}/{}", retry + 1, MAX_RETRIES, e);
                instance.failureCount.incrementAndGet();
                browserPool.offer(instance); // 返回不健康的实例
            }
        }

        throw new BrowserPoolExhaustedException(
                "浏览器池耗尽，重试 " + MAX_RETRIES + " 次均失败");
    }

    /**
     * 关闭Page并归还浏览器实例
     * 必须在finally块或try-with-resources中调用
     */
    public void closePage(PageWrapper wrapper) {
        if (wrapper == null)
            return;

        try {
            // 1. 关闭Page
            try {
                if (wrapper.page != null && !wrapper.page.isClosed()) {
                    wrapper.page.close();
                }
            } catch (Exception e) {
                log.warn("关闭Page失败", e);
            }

            // 2. 关闭Context
            try {
                if (wrapper.context != null) {
                    wrapper.context.close();
                }
            } catch (Exception e) {
                log.warn("关闭BrowserContext失败", e);
            }

            // 3. 从活跃集合中移除
            if (wrapper.instance != null && wrapper.pageId != null) {
                wrapper.instance.activePageIds.remove(wrapper.pageId);
            }
        } catch (Exception e) {
            log.error("关闭Page过程中出错", e);
        } finally {
            // 4. 归还浏览器实例到池（即使关闭失败）
            if (wrapper.instance != null && !isShuttingDown) {
                try {
                    browserPool.offer(wrapper.instance);
                } catch (Exception e) {
                    log.warn("归还浏览器实例失败", e);
                }
            }
        }
    }

    /**
     * 定期健康检查
     * 检查浏览器实例是否仍然可用，不健康的实例会被重建
     */
    private void scheduleHealthCheck() {
        healthChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "browser-health-checker");
            t.setDaemon(true);
            return t;
        });

        healthChecker.scheduleAtFixedRate(() -> {
            try {
                List<BrowserInstance> instances = new ArrayList<>();
                browserPool.drainTo(instances);

                for (BrowserInstance instance : instances) {
                    if (instance.isHealthy()) {
                        browserPool.offer(instance);
                    } else {
                        log.warn("检测到不健康的浏览器实例，正在重建");
                        instance.close();
                        try {
                            BrowserInstance newInstance = createBrowserInstance(-1);
                            browserPool.offer(newInstance);
                        } catch (Exception e) {
                            log.error("重建浏览器实例失败", e);
                        }
                    }
                }

                log.debug("浏览器健康检查完成: {} 个健康实例", browserPool.size());
            } catch (Exception e) {
                log.error("浏览器健康检查失败", e);
            }
        }, HEALTH_CHECK_INTERVAL_SECONDS, HEALTH_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("浏览器健康检查已启动，检查间隔: {}秒", HEALTH_CHECK_INTERVAL_SECONDS);
    }

    /**
     * 获取当前池中可用实例数
     */
    public int getAvailableInstanceCount() {
        return browserPool.size();
    }

    /**
     * 获取所有活跃Page总数
     */
    public int getTotalActivePages() {
        List<BrowserInstance> instances = new ArrayList<>();
        browserPool.drainTo(instances);
        try {
            return instances.stream()
                    .mapToInt(inst -> inst.activePageIds.size())
                    .sum();
        } finally {
            instances.forEach(inst -> browserPool.offer(inst));
        }
    }

    /**
     * Spring容器关闭时的清理逻辑
     * 实现DisposableBean接口
     */
    @Override
    public void destroy() {
        log.info("开始关闭浏览器实例池...");
        isShuttingDown = true;

        try {
            // 1. 停止健康检查
            if (healthChecker != null) {
                healthChecker.shutdown();
                try {
                    if (!healthChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("健康检查器未在规定时间内关闭，正在强制关闭");
                        healthChecker.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.warn("等待健康检查器关闭被中断", e);
                    healthChecker.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // 2. 关闭所有浏览器实例
            List<BrowserInstance> instances = new ArrayList<>();
            browserPool.drainTo(instances);
            log.info("关闭 {} 个浏览器实例", instances.size());

            for (BrowserInstance instance : instances) {
                try {
                    instance.close();
                } catch (Exception e) {
                    log.warn("关闭浏览器实例失败", e);
                }
            }

            // 3. 关闭Playwright
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception e) {
                    log.warn("关闭Playwright失败", e);
                }
            }

            log.info("浏览器实例池已完全关闭");
        } catch (Exception e) {
            log.error("关闭浏览器实例池过程中出错", e);
        }
    }

    /**
     * 浏览器池耗尽异常
     */
    public static class BrowserPoolExhaustedException extends RuntimeException {
        public BrowserPoolExhaustedException(String message) {
            super(message);
        }

        public BrowserPoolExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
