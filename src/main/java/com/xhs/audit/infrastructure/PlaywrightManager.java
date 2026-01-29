package com.xhs.audit.infrastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Playwright浏览器实例池管理器
 *
 * 职责：
 * 1. 维护多个浏览器实例的生命周期（每个Browser完全独立）
 * 2. 每个Browser实例同一时刻只允许一个Page操作（避免CDP冲突）
 * 3. 等待队列机制，超出容量则等待或拒绝
 * 4. 空闲30秒自动释放浏览器实例
 * 5. 优雅关闭时释放所有资源
 *
 * 使用示例：
 *
 * <pre>
 * try {
 *     PlaywrightManager.PageWrapper page = manager.borrowPage();
 *     page.page.navigate("https://example.com");
 *     // 爬虫操作（所有CDP操作在Page上是串行的）
 * } finally {
 *     manager.closePage(page);
 * }
 * </pre>
 */
@Slf4j
@Component
public class PlaywrightManager implements DisposableBean {

    private static final int POOL_SIZE = 10;
    private static final long BORROW_TIMEOUT_SECONDS = 10;
    private static final int MAX_FAILURES_THRESHOLD = 3;
    private static final long IDLE_TIMEOUT_SECONDS = 30;
    private static final int WAITING_QUEUE_SIZE = 10000;
    private static final long BROWSER_COOLDOWN_MS = 2000; // Browser 冷却时间

    @org.springframework.beans.factory.annotation.Value("${audit.crawler.headless:true}")
    private boolean headless;

    // 浏览器实例集合
    private final java.util.Set<BrowserInstance> activeInstances = ConcurrentHashMap.newKeySet();

    // 等待队列：存储等待获取Page的请求
    private final BlockingQueue<WaitingRequest> waitingQueue = new LinkedBlockingQueue<>(WAITING_QUEUE_SIZE);

    // 空闲实例队列：存储空闲的浏览器实例（每个实例包含完整的 Browser + Context + Page）
    private final BlockingQueue<BrowserInstance> idlePool = new LinkedBlockingQueue<>(POOL_SIZE);

    private ScheduledExecutorService idleChecker;
    private ScheduledExecutorService waiterProcessor;
    private Playwright playwright;
    private volatile boolean isShuttingDown = false;

    // 统计信息
    private final AtomicInteger totalCreatedBrowsers = new AtomicInteger(0);
    private final AtomicInteger totalRejectedRequests = new AtomicInteger(0);
    private final AtomicInteger totalWaitTimeouts = new AtomicInteger(0);

    /**
     * 等待请求包装类
     */
    static class WaitingRequest {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger(0); // 0: waiting, 1: got page, -1: rejected/timeout
        volatile PageWrapper pageWrapper;
        volatile String errorMessage;

        boolean complete(PageWrapper wrapper) {
            this.pageWrapper = wrapper;
            return result.compareAndSet(0, 1);
        }

        boolean reject(String reason) {
            this.errorMessage = reason;
            return result.compareAndSet(0, -1);
        }

        boolean isDone() {
            return result.get() != 0;
        }
    }

    /**
     * 浏览器实例包装类
     * 每个实例包含一个完整的 Browser + Context + Page 组合
     * 同一时刻只允许一个线程操作，避免 CDP 冲突
     */
    static class BrowserInstance {
        final String id = UUID.randomUUID().toString();
        Browser browser;
        BrowserContext context;
        Page page;
        final AtomicInteger failureCount = new AtomicInteger(0);
        volatile long lastActivityTime;
        final AtomicInteger usageCount = new AtomicInteger(0);
        volatile boolean isInUse = false;
        volatile long cooldownUntil = 0; // 冷却期结束时间

        BrowserInstance() {
            this.lastActivityTime = System.currentTimeMillis();
        }

        /**
         * 标记为使用中
         */
        boolean acquire() {
            return compareAndSetInUse(false, true);
        }

        /**
         * 检查是否在冷却期
         */
        boolean isInCooldown() {
            return System.currentTimeMillis() < cooldownUntil;
        }

        /**
         * 进入冷却期
         */
        void enterCooldown() {
            this.cooldownUntil = System.currentTimeMillis() + BROWSER_COOLDOWN_MS;
        }

        /**
         * 释放
         */
        void release() {
            isInUse = false;
            lastActivityTime = System.currentTimeMillis();
            enterCooldown();
        }

        private boolean compareAndSetInUse(boolean expected, boolean update) {
            synchronized (this) {
                if (isInUse == expected) {
                    isInUse = update;
                    return true;
                }
                return false;
            }
        }

        /**
         * 检查是否健康
         */
        boolean isHealthy() {
            try {
                // 尝试访问 browser 确保存活
                browser.contexts().size();
                // 检查 page 是否仍然有效
                if (page == null || page.isClosed()) {
                    return false;
                }
                return failureCount.get() < MAX_FAILURES_THRESHOLD;
            } catch (Exception e) {
                failureCount.incrementAndGet();
                return false;
            }
        }

        /**
         * 关闭浏览器实例
         */
        void close() {
            try {
                // 关闭Page
                try {
                    if (page != null && !page.isClosed()) {
                        page.close();
                    }
                } catch (Exception e) {
                    log.warn("关闭Page失败", e);
                }

                // 关闭Context
                try {
                    if (context != null) {
                        context.close();
                    }
                } catch (Exception e) {
                    log.warn("关闭BrowserContext失败", e);
                }

                // 关闭Browser
                try {
                    if (browser != null) {
                        browser.close();
                    }
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
        private final PlaywrightManager manager;

        public PageWrapper(Page page, BrowserContext context, BrowserInstance instance, PlaywrightManager manager) {
            this.page = page;
            this.context = context;
            this.instance = instance;
            this.manager = manager;
        }

        @Override
        public void close() {
            manager.closePage(this);
        }
    }

    /**
     * 初始化浏览器实例池
     */
    @PostConstruct
    public void initBrowserPool() {
        log.info("正在初始化浏览器实例池，最大浏览器数: {}, 无头模式: {}, 等待队列: {}",
                POOL_SIZE, headless, WAITING_QUEUE_SIZE);

        try {
            playwright = Playwright.create();

            // 启动空闲检测线程
            scheduleIdleCheck();

            // 启动等待队列处理器
            startWaiterProcessor();

            log.info("浏览器实例池初始化完成");

        } catch (Exception e) {
            log.error("浏览器实例池初始化失败", e);
            throw new RuntimeException("PlaywrightManager初始化失败", e);
        }
    }

    /**
     * 创建单个浏览器实例（包含 Browser + Context + Page）
     * 带重试机制，应对 CDP 暂时性故障
     */
    private BrowserInstance createBrowserInstance() {
        int maxRetries = 3;
        long retryDelayMs = 500;

        for (int retry = 0; retry < maxRetries; retry++) {
            BrowserInstance instance = new BrowserInstance();

            try {
                // 启动Chromium浏览器
                instance.browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(headless)
                                .setArgs(Arrays.asList(
                                        "--disable-blink-features=AutomationControlled",
                                        "--no-sandbox",
                                        "--disable-gpu")));

                // 短暂延迟，确保浏览器完全启动
                Thread.sleep(100);

                // 创建BrowserContext（模拟iPhone 13 Pro）
                instance.context = instance.browser.newContext(
                        new Browser.NewContextOptions()
                                .setUserAgent(
                                        "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) " +
                                                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1")
                                .setViewportSize(390, 844)
                                .setLocale("zh-CN"));

                // 短暂延迟，确保Context创建完成
                Thread.sleep(100);

                // 创建Page
                instance.page = instance.context.newPage();
                instance.acquire();

                instance.failureCount.set(0);
                totalCreatedBrowsers.incrementAndGet();

                log.debug("创建浏览器实例: {}", instance.id);
                return instance;

            } catch (Exception e) {
                log.warn("创建浏览器实例失败 (尝试 {}/{}): {}",
                        retry + 1, maxRetries, e.getMessage());
                if (instance != null) {
                    try {
                        instance.close();
                    } catch (Exception closeEx) {
                        log.warn("关闭失败的浏览器实例时出错: {}", closeEx.getMessage());
                    }
                }

                if (retry < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelayMs * (retry + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("创建浏览器被中断", ie);
                    }
                }
            }
        }

        throw new RuntimeException("无法创建浏览器实例，已达到最大重试次数");
    }

    /**
     * 从池中借用Page
     *
     * @return PageWrapper 页面包装器，使用完后必须关闭
     * @throws BrowserPoolExhaustedException 如果等待队列已满或等待超时
     */
    public PageWrapper borrowPage() throws BrowserPoolExhaustedException {
        if (isShuttingDown) {
            throw new IllegalStateException("浏览器池正在关闭，无法借用新Page");
        }

        // 1. 查找空闲的浏览器实例
        BrowserInstance instance = findIdleInstance();

        if (instance != null) {
            // 检查page是否有效，如果无效则创建新的page
            PageWrapper wrapper = ensurePageValid(instance);
            if (wrapper != null) {
                return wrapper;
            }
        }

        // 2. 没有空闲实例，检查是否可创建新的
        if (activeInstances.size() < POOL_SIZE) {
            synchronized (poolLock) {
                instance = findIdleInstance();
                if (instance != null) {
                    PageWrapper wrapper = ensurePageValid(instance);
                    if (wrapper != null) {
                        return wrapper;
                    }
                }

                // 创建新实例
                if (activeInstances.size() < POOL_SIZE) {
                    instance = createBrowserInstance();
                    activeInstances.add(instance);
                    log.info("创建新浏览器实例，当前活跃实例数: {}", activeInstances.size());
                    return new PageWrapper(instance.page, instance.context, instance, this);
                }
            }
        }

        // 3. 所有实例都在使用中，进入等待队列
        return waitForAvailableInstance();
    }

    /**
     * 确保实例的page有效，如果无效则创建新的page
     */
    private PageWrapper ensurePageValid(BrowserInstance instance) {
        try {
            // 检查page是否存在且未关闭
            if (instance.page != null && !instance.page.isClosed()) {
                return new PageWrapper(instance.page, instance.context, instance, this);
            }

            // page无效，创建新的page
            log.debug("实例 {} 的page无效，创建新page", instance.id);
            instance.page = instance.context.newPage();
            return new PageWrapper(instance.page, instance.context, instance, this);
        } catch (Exception e) {
            log.warn("创建新page失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 查找空闲的浏览器实例（未被使用且不在冷却期）
     */
    private BrowserInstance findIdleInstance() {
        for (BrowserInstance instance : activeInstances) {
            // 检查是否可用：不在使用中、不在冷却期、健康
            if (!instance.isInUse && !instance.isInCooldown() && instance.isHealthy()) {
                if (instance.acquire()) {
                    return instance;
                }
            }
        }
        return null;
    }

    /**
     * 等待获取可用实例
     */
    private PageWrapper waitForAvailableInstance() throws BrowserPoolExhaustedException {
        WaitingRequest request = new WaitingRequest();

        // 尝试加入等待队列
        if (!waitingQueue.offer(request)) {
            totalRejectedRequests.incrementAndGet();
            throw new BrowserPoolExhaustedException("等待队列已满，请求被拒绝");
        }

        try {
            // 等待被唤醒或超时
            boolean completed = request.latch.await(BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed || request.isDone()) {
                if (request.isDone()) {
                    if (request.result.get() == 1 && request.pageWrapper != null) {
                        return request.pageWrapper;
                    } else {
                        throw new BrowserPoolExhaustedException(request.errorMessage != null ?
                                request.errorMessage : "等待被中断");
                    }
                } else {
                    totalWaitTimeouts.incrementAndGet();
                    waitingQueue.remove(request);
                    throw new BrowserPoolExhaustedException("等待获取Page超时");
                }
            }

            waitingQueue.remove(request);
            throw new BrowserPoolExhaustedException("等待获取Page超时");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            waitingQueue.remove(request);
            throw new BrowserPoolExhaustedException("等待被中断");
        }
    }

    /**
     * 关闭Page并释放实例回池
     */
    public void closePage(PageWrapper wrapper) {
        if (wrapper == null)
            return;

        BrowserInstance instance = wrapper.instance;

        try {
            // 检查Page是否仍然有效
            if (wrapper.page != null && !wrapper.page.isClosed()) {
                try {
                    wrapper.page.close();
                } catch (Exception e) {
                    log.warn("关闭Page失败", e);
                }
            }

            // 释放实例（回到池中）
            if (instance != null) {
                // 不再清除Page引用，让isHealthy()检查页面状态
                // 如果页面已关闭，isHealthy()会返回false

                log.debug("释放浏览器实例: {}, 使用次数: {}",
                        instance.id, instance.usageCount.incrementAndGet());

                // 如果正在关闭，销毁实例
                if (isShuttingDown) {
                    instance.close();
                    activeInstances.remove(instance);
                } else {
                    // 释放回池
                    instance.release();
                    addToIdlePool(instance);
                }
            }
        } catch (Exception e) {
            log.error("关闭Page过程中出错", e);
        } finally {
            // 唤醒等待队列中的请求
            processWaitingRequests();
        }
    }

    /**
     * 将空闲实例加入空闲池
     */
    private void addToIdlePool(BrowserInstance instance) {
        if (isShuttingDown)
            return;

        try {
            // 30秒后自动释放
            idlePool.offer(instance);
        } catch (Exception e) {
            log.warn("归还空闲实例失败", e);
        }
    }

    /**
     * 启动等待队列处理器
     */
    private void startWaiterProcessor() {
        waiterProcessor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "waiter-processor");
            t.setDaemon(true);
            return t;
        });

        waiterProcessor.scheduleAtFixedRate(() -> {
            try {
                processWaitingRequests();
            } catch (Exception e) {
                log.error("处理等待队列失败", e);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理等待队列中的请求
     */
    private void processWaitingRequests() {
        if (waitingQueue.isEmpty())
            return;

        BrowserInstance instance = findIdleInstance();

        if (instance == null && activeInstances.size() < POOL_SIZE) {
            synchronized (poolLock) {
                instance = findIdleInstance();
                if (instance == null && activeInstances.size() < POOL_SIZE) {
                    instance = createBrowserInstance();
                    activeInstances.add(instance);
                }
            }
        }

        if (instance != null) {
            // 唤醒一个等待请求
            WaitingRequest request = waitingQueue.poll();
            if (request != null) {
                try {
                    PageWrapper wrapper = new PageWrapper(instance.page, instance.context, instance, this);
                    if (request.complete(wrapper)) {
                        request.latch.countDown();
                        log.debug("唤醒等待请求，成功分配浏览器实例");
                    }
                } catch (Exception e) {
                    request.reject("分配实例失败: " + e.getMessage());
                    request.latch.countDown();
                }
            }
        }
    }

    /**
     * 启动空闲检测线程
     */
    private void scheduleIdleCheck() {
        idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "browser-idle-checker");
            t.setDaemon(true);
            return t;
        });

        idleChecker.scheduleAtFixedRate(() -> {
            try {
                checkAndReleaseIdleInstances();
            } catch (Exception e) {
                log.error("空闲检测失败", e);
            }
        }, IDLE_TIMEOUT_SECONDS, IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 检查并释放空闲的浏览器实例
     */
    private void checkAndReleaseIdleInstances() {
        if (isShuttingDown)
            return;

        long now = System.currentTimeMillis();
        List<BrowserInstance> toRelease = new ArrayList<>();

        // 检查空闲池中的实例
        idlePool.drainTo(toRelease);

        for (BrowserInstance instance : toRelease) {
            // 检查空闲时间
            if (now - instance.lastActivityTime >= IDLE_TIMEOUT_SECONDS * 1000) {
                log.info("释放空闲浏览器实例: {}, 总使用次数: {}",
                        instance.id, instance.usageCount.get());
                instance.close();
                activeInstances.remove(instance);
            } else {
                // 未超时，重新加入空闲池
                idlePool.offer(instance);
            }
        }

        log.debug("空闲检测完成，活跃实例: {}, 空闲池: {}, 等待队列: {}",
                activeInstances.size(), idlePool.size(), waitingQueue.size());
    }

    // 池操作锁
    private final Object poolLock = new Object();

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("活跃实例: %d, 空闲池: %d, 等待队列: %d, 已创建: %d, 已拒绝: %d, 超时: %d",
                activeInstances.size(), idlePool.size(), waitingQueue.size(),
                totalCreatedBrowsers.get(), totalRejectedRequests.get(), totalWaitTimeouts.get());
    }

    /**
     * 获取当前活跃实例数
     */
    public int getActiveInstanceCount() {
        return activeInstances.size();
    }

    /**
     * Spring容器关闭时的清理逻辑
     */
    @Override
    public void destroy() {
        log.info("开始关闭浏览器实例池... 统计: {}", getStats());
        isShuttingDown = true;

        try {
            // 1. 停止后台线程
            if (idleChecker != null) {
                idleChecker.shutdown();
                try { idleChecker.awaitTermination(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
            if (waiterProcessor != null) {
                waiterProcessor.shutdown();
                try { waiterProcessor.awaitTermination(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }

            // 2. 唤醒所有等待请求
            waitingQueue.forEach(request -> {
                request.reject("系统关闭");
                request.latch.countDown();
            });
            waitingQueue.clear();

            // 3. 关闭所有浏览器实例
            for (BrowserInstance instance : activeInstances) {
                try {
                    instance.close();
                } catch (Exception e) {
                    log.warn("关闭浏览器实例失败", e);
                }
            }
            activeInstances.clear();
            idlePool.clear();

            // 4. 关闭Playwright
            if (playwright != null) {
                playwright.close();
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
