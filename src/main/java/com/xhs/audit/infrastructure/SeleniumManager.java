package com.xhs.audit.infrastructure;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Selenium Grid 浏览器实例池管理器
 *
 * 职责：
 * 1. 管理多个浏览器实例的生命周期（支持多线程并发）
 * 2. 等待队列机制，超出容量则等待或拒绝
 * 3. 空闲自动释放浏览器实例
 * 4. 优雅关闭时释放所有资源
 *
 * 使用示例：
 *
 * <pre>
 * try {
 *     SeleniumManager.DriverWrapper driver = manager.borrowDriver();
 *     driver.driver.get("https://example.com");
 *     // 爬虫操作
 * } finally {
 *     manager.closeDriver(driver);
 * }
 * </pre>
 */
@Slf4j
@Component
public class SeleniumManager implements DisposableBean {

    private static final int DEFAULT_POOL_SIZE = 4; // 默认4个浏览器实例，支持并发
    private static final long BORROW_TIMEOUT_SECONDS = 30;
    private static final int MAX_FAILURES_THRESHOLD = 3;
    private static final long IDLE_TIMEOUT_SECONDS = 120;
    private static final int WAITING_QUEUE_SIZE = 10000;
    private static final long DRIVER_COOLDOWN_MS = 100;

    @Value("${audit.crawler.selenium.grid-url:http://localhost:4444}")
    private String seleniumGridUrl;

    @Value("${audit.crawler.selenium.pool-size:4}")
    private int poolSize = DEFAULT_POOL_SIZE;

    @Value("${audit.crawler.headless:true}")
    private boolean headless;

    @Value("${audit.crawler.selenium.page-load-timeout:30}")
    private int pageLoadTimeout;

    @Value("${audit.crawler.selenium.implicit-wait:10}")
    private int implicitWait;

    @Value("${audit.crawler.selenium.mobile-mode:true}")
    private boolean mobileMode;

    // 浏览器实例集合
    private final java.util.Set<DriverInstance> activeInstances = ConcurrentHashMap.newKeySet();

    // 等待队列：存储等待获取Driver的请求
    private final BlockingQueue<WaitingRequest> waitingQueue = new LinkedBlockingQueue<>(WAITING_QUEUE_SIZE);

    // 空闲实例队列：存储空闲的浏览器实例
    private final BlockingQueue<DriverInstance> idlePool = new LinkedBlockingQueue<>();

    private ScheduledExecutorService idleChecker;
    private ScheduledExecutorService waiterProcessor;
    private volatile boolean isShuttingDown = false;

    // 统计信息
    private final AtomicInteger totalCreatedDrivers = new AtomicInteger(0);
    private final AtomicInteger totalRejectedRequests = new AtomicInteger(0);
    private final AtomicInteger totalWaitTimeouts = new AtomicInteger(0);

    /**
     * 等待请求包装类
     */
    static class WaitingRequest {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger(0); // 0: waiting, 1: got driver, -1: rejected/timeout
        volatile DriverWrapper driverWrapper;
        volatile String errorMessage;

        boolean complete(DriverWrapper wrapper) {
            this.driverWrapper = wrapper;
            return result.compareAndSet(0, 1);
        }

        boolean reject(String reason) {
            this.errorMessage = reason;
            return result.compareAndSet(0, -1);
        }
    }

    /**
     * 浏览器实例包装类
     */
    static class DriverInstance {
        final String id;
        final WebDriver driver;
        final long createdAt;
        volatile long lastUsedAt;
        volatile boolean isBusy;
        final AtomicInteger failureCount;

        DriverInstance(String id, WebDriver driver) {
            this.id = id;
            this.driver = driver;
            this.createdAt = System.currentTimeMillis();
            this.lastUsedAt = this.createdAt;
            this.isBusy = false;
            this.failureCount = new AtomicInteger(0);
        }

        boolean isHealthy() {
            return failureCount.get() < MAX_FAILURES_THRESHOLD;
        }

        long getIdleTime() {
            return System.currentTimeMillis() - lastUsedAt;
        }
    }

    /**
     * Driver包装类（对外暴露）
     */
    public static class DriverWrapper implements AutoCloseable {
        public final WebDriver driver;
        private final DriverInstance instance;
        private final SeleniumManager manager;

        public DriverWrapper(WebDriver driver, DriverInstance instance, SeleniumManager manager) {
            this.driver = driver;
            this.instance = instance;
            this.manager = manager;
        }

        /**
         * 标记此 Driver 为不健康状态（发生异常时调用）
         */
        public void markUnhealthy() {
            if (instance != null) {
                instance.failureCount.incrementAndGet();
                log.warn("Driver标记为不健康: instanceId={}, failureCount={}",
                        instance.id, instance.failureCount.get());
            }
        }

        @Override
        public void close() {
            manager.returnDriver(this);
        }
    }

    @PostConstruct
    public void initialize() {
        log.info("初始化 SeleniumManager: gridUrl={}, poolSize={}, headless={}, mobileMode={}",
                seleniumGridUrl, poolSize, headless, mobileMode);

        // 启动空闲检查器
        idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "selenium-idle-checker");
            t.setDaemon(true);
            return t;
        });
        idleChecker.scheduleAtFixedRate(this::checkAndReleaseIdleInstances, 30, 30, TimeUnit.SECONDS);

        // 启动等待队列处理器
        waiterProcessor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "selenium-waiter-processor");
            t.setDaemon(true);
            return t;
        });
        waiterProcessor.scheduleAtFixedRate(this::processWaitingQueue, 100, 100, TimeUnit.MILLISECONDS);

        log.info("SeleniumManager 初始化完成");
    }

    /**
     * 借用一个 Driver
     */
    public DriverWrapper borrowDriver() throws BrowserPoolExhaustedException {
        if (isShuttingDown) {
            throw new BrowserPoolExhaustedException("SeleniumManager 正在关闭");
        }

        // 1. 尝试从空闲池获取
        DriverInstance instance = idlePool.poll();
        if (instance != null) {
            if (instance.isHealthy()) {
                instance.isBusy = true;
                instance.lastUsedAt = System.currentTimeMillis();
                log.debug("复用空闲Driver: instanceId={}", instance.id);
                return new DriverWrapper(instance.driver, instance, this);
            } else {
                log.warn("空闲Driver不健康，销毁: instanceId={}", instance.id);
                destroyDriverInstance(instance);
            }
        }

        // 2. 尝试创建新实例
        if (activeInstances.size() < poolSize) {
            try {
                instance = createDriverInstance();
                instance.isBusy = true;
                log.info("创建新Driver实例: instanceId={}, 当前活跃数={}/{}",
                        instance.id, activeInstances.size(), poolSize);
                return new DriverWrapper(instance.driver, instance, this);
            } catch (Exception e) {
                log.error("创建Driver失败", e);
                throw new BrowserPoolExhaustedException("创建Driver失败: " + e.getMessage(), e);
            }
        }

        // 3. 进入等待队列
        log.debug("Driver池已满，进入等待队列");
        WaitingRequest request = new WaitingRequest();

        if (!waitingQueue.offer(request)) {
            totalRejectedRequests.incrementAndGet();
            throw new BrowserPoolExhaustedException(
                    "等待队列已满(" + WAITING_QUEUE_SIZE + ")，拒绝请求");
        }

        try {
            boolean gotDriver = request.latch.await(BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!gotDriver || request.result.get() != 1) {
                totalWaitTimeouts.incrementAndGet();
                throw new BrowserPoolExhaustedException(
                        request.errorMessage != null ? request.errorMessage : "等待Driver超时");
            }

            return request.driverWrapper;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrowserPoolExhaustedException("等待Driver被中断", e);
        }
    }

    /**
     * 归还 Driver
     */
    private void returnDriver(DriverWrapper wrapper) {
        if (wrapper == null || wrapper.instance == null) {
            return;
        }

        DriverInstance instance = wrapper.instance;
        instance.lastUsedAt = System.currentTimeMillis();
        instance.isBusy = false;

        if (!instance.isHealthy() || isShuttingDown) {
            log.warn("Driver不健康或正在关闭，销毁: instanceId={}", instance.id);
            destroyDriverInstance(instance);
            return;
        }

        // 放回空闲池
        idlePool.offer(instance);
        log.debug("Driver已归还到空闲池: instanceId={}", instance.id);
    }

    /**
     * 创建 Driver 实例
     */
    private DriverInstance createDriverInstance() throws MalformedURLException {
        String instanceId = "driver-" + totalCreatedDrivers.incrementAndGet();

        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            options.addArguments("--headless");
        }

        // 设置页面加载策略为 eager - 不等待所有资源加载完成
        options.setPageLoadStrategy(org.openqa.selenium.PageLoadStrategy.EAGER);

        // 根据模式设置User-Agent和窗口大小
        if (mobileMode) {
            // 模拟iPhone 13 Pro
            String mobileUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1";

            // Firefox 使用 preferences 设置 User-Agent
            options.addPreference("general.useragent.override", mobileUserAgent);

            // 模拟触摸事件
            options.addPreference("dom.w3c_touch_events.enabled", 1);

            // 设置移动设备视口
            options.addPreference("layout.css.devPixelsPerPx", "2.0");

            log.debug("使用移动设备模式: iPhone User-Agent - {}", mobileUserAgent);
        } else {
            // 桌面浏览器
            String desktopUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
            options.addPreference("general.useragent.override", desktopUserAgent);
        }

        // 禁用自动化检测
        options.addPreference("dom.webdriver.enabled", false);
        options.addPreference("useAutomationExtension", false);

        // 增强反爬虫策略
        options.addPreference("media.peerconnection.enabled", false); // 禁用 WebRTC
        options.addPreference("geo.enabled", false); // 禁用地理位置
        options.addPreference("permissions.default.image", 2); // 禁用图片加载，加快速度
        options.addPreference("dom.ipc.plugins.enabled.libflashplayer.so", false);

        // 禁用通知
        options.addPreference("dom.webnotifications.enabled", false);
        options.addPreference("dom.push.enabled", false);

        // 连接到 Selenium Grid
        RemoteWebDriver driver = new RemoteWebDriver(new URL(seleniumGridUrl), options);

        // 设置超时
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoadTimeout));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitWait));

        // 设置窗口大小
        if (mobileMode) {
            // iPhone 13 Pro 分辨率: 390x844
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(390, 844));
            log.debug("设置移动设备窗口大小: 390x844");
        } else {
            // 桌面浏览器默认大小
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(1920, 1080));
        }

        DriverInstance instance = new DriverInstance(instanceId, driver);
        activeInstances.add(instance);

        log.info("Driver实例创建成功: instanceId={}, gridUrl={}", instanceId, seleniumGridUrl);
        return instance;
    }

    /**
     * 销毁 Driver 实例
     */
    private void destroyDriverInstance(DriverInstance instance) {
        if (instance == null) {
            return;
        }

        activeInstances.remove(instance);

        try {
            if (instance.driver != null) {
                instance.driver.quit();
            }
            log.info("Driver实例已销毁: instanceId={}", instance.id);
        } catch (Exception e) {
            log.warn("销毁Driver异常: instanceId={}, error={}", instance.id, e.getMessage());
        }
    }

    /**
     * 处理等待队列
     */
    private void processWaitingQueue() {
        try {
            while (!waitingQueue.isEmpty() && !isShuttingDown) {
                DriverInstance instance = idlePool.poll();
                if (instance == null) {
                    break;
                }

                if (!instance.isHealthy()) {
                    destroyDriverInstance(instance);
                    continue;
                }

                WaitingRequest request = waitingQueue.poll();
                if (request == null) {
                    idlePool.offer(instance);
                    break;
                }

                instance.isBusy = true;
                instance.lastUsedAt = System.currentTimeMillis();
                DriverWrapper wrapper = new DriverWrapper(instance.driver, instance, this);

                if (request.complete(wrapper)) {
                    request.latch.countDown();
                    log.debug("等待请求已满足: instanceId={}", instance.id);
                } else {
                    // 请求已超时或被拒绝
                    idlePool.offer(instance);
                }
            }
        } catch (Exception e) {
            log.error("处理等待队列异常", e);
        }
    }

    /**
     * 检查并释放空闲实例
     */
    private void checkAndReleaseIdleInstances() {
        try {
            long now = System.currentTimeMillis();
            int releasedCount = 0;

            for (DriverInstance instance : new java.util.ArrayList<>(activeInstances)) {
                if (!instance.isBusy && instance.getIdleTime() > IDLE_TIMEOUT_SECONDS * 1000) {
                    idlePool.remove(instance);
                    destroyDriverInstance(instance);
                    releasedCount++;
                }
            }

            if (releasedCount > 0) {
                log.info("释放空闲Driver实例: count={}, 剩余活跃数={}", releasedCount, activeInstances.size());
            }
        } catch (Exception e) {
            log.error("检查空闲实例异常", e);
        }
    }

    /**
     * Driver池耗尽异常
     */
    public static class BrowserPoolExhaustedException extends RuntimeException {
        public BrowserPoolExhaustedException(String message) {
            super(message);
        }

        public BrowserPoolExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    public void destroy() throws Exception {
        log.info("开始关闭 SeleniumManager...");
        isShuttingDown = true;

        // 1. 关闭调度器
        if (idleChecker != null) {
            idleChecker.shutdown();
            idleChecker.awaitTermination(5, TimeUnit.SECONDS);
        }

        if (waiterProcessor != null) {
            waiterProcessor.shutdown();
            waiterProcessor.awaitTermination(5, TimeUnit.SECONDS);
        }

        // 2. 拒绝所有等待请求
        WaitingRequest request;
        while ((request = waitingQueue.poll()) != null) {
            if (request.reject("SeleniumManager正在关闭")) {
                request.latch.countDown();
            }
        }

        // 3. 关闭所有Driver实例
        for (DriverInstance instance : new java.util.ArrayList<>(activeInstances)) {
            destroyDriverInstance(instance);
        }

        log.info("SeleniumManager 已关闭. 总创建Driver数={}, 总拒绝请求数={}, 总超时数={}",
                totalCreatedDrivers.get(), totalRejectedRequests.get(), totalWaitTimeouts.get());
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format(
                "SeleniumManager统计 - 活跃实例:%d/%d, 空闲:%d, 等待:%d, 总创建:%d, 总拒绝:%d, 总超时:%d",
                activeInstances.size(), poolSize, idlePool.size(), waitingQueue.size(),
                totalCreatedDrivers.get(), totalRejectedRequests.get(), totalWaitTimeouts.get());
    }
}
