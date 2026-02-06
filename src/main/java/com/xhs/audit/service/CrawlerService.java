package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xhs.audit.infrastructure.SeleniumManager;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.XhsContentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 爬虫服务 - 基于 Selenium Grid 的实现
 *
 * 核心功能:
 * 1. 三级缓存检查 (Redis -> PostgreSQL -> 爬虫)
 * 2. 基于Selenium Grid的分布式爬虫（支持多线程）
 * 3. 异常处理和重试机制
 * 4. 数据持久化
 *
 * @author Bruce
 * @since 2026-02-02
 */
@Service
@Slf4j
public class CrawlerService {

    @Autowired
    private SeleniumManager seleniumManager;

    @Autowired
    private XhsContentRepository contentRepository;

    @Autowired(required = false)
    private RedisTemplate<String, XhsContent> redisTemplate;

    private static final String CONTENT_CACHE_PREFIX = "xhs:content:";
    private static final long CACHE_TTL_SECONDS = 86400;
    // 重试次数增加到5次，应对可能连续拿到快要失败的Driver的情况
    // poolSize=4, MAX_FAILURES_THRESHOLD=3, 5次重试可以覆盖最坏场景
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long[] RETRY_DELAYS_MS = { 500, 1000, 1500, 2000, 2500 };

    // ============ Selenium 爬虫常量 ============
    /** 页面内容加载超时时间（秒） */
    private static final int CONTENT_LOAD_TIMEOUT_SECONDS = 10;
    /** 滚动步长（像素） */
    private static final int SCROLL_STEP = 300;
    /** 最大滚动次数 */
    private static final int MAX_SCROLLS = 3;
    /** 滚动随机范围 */
    private static final int SCROLL_RANDOM_RANGE = 200;
    /** 滚动等待基础时间（毫秒） */
    private static final int SCROLL_WAIT_BASE_MS = 100;
    /** 滚动等待随机范围（毫秒） */
    private static final int SCROLL_WAIT_RANDOM_MS = 100;
    /** 回顶部等待时间（毫秒） */
    private static final int SCROLL_TO_TOP_WAIT_MS = 300;

    // 支持标准小红书链接和 xhslink.com 短链接
    private static final Pattern XHS_URL_PATTERN = Pattern.compile(
            "https://(?:www\\.)?xiaohongshu\\.com/(?:explore|discovery/item)/([a-zA-Z0-9_-]+)");
    private static final Pattern XHS_SHORTLINK_PATTERN = Pattern.compile(
            "https?://xhslink\\.com/o/([a-zA-Z0-9]+)");
    private static final String[] TITLE_SELECTORS = {
            "#detail-title",
            "[class*='title']"
    };
    private static final String[] DESCRIPTION_SELECTORS = {
            "#detail-desc > span > span",
            "[class*='desc']",
            "[class*='note-content']",
            "[class*='content']",
            "[class*='detail']",
            "article",
            ".content",
            ".note-text"
    };
    private static final String[] IMAGE_SELECTORS = {
            "#noteContainer > div.media-container > div > div > div.swiper.swiper-initialized.swiper-horizontal.swiper-pointer-events.swiper-watch-progress.note-slider.swiper-backface-hidden > div > div.swiper-slide.swiper-slide-visible.swiper-slide-active.swiper-slide-duplicate-next.swiper-slide-duplicate-prev > div > div > img",
            "img[class*='image']"
    };
    private static final String[] TAG_SELECTORS = {
            "#hash-tag",
            "[class*='tag']"
    };
    private static final String[] AUTHOR_SELECTORS = {
            "#noteContainer > div.author > div > div.info > a.name > span",
            "[class*='author']"
    };
    private static final String[] PUBLISH_TIME_SELECTORS = {
            "#noteContainer > div.interaction-container > div.note-scroller > div.note-content > div.bottom-container > span.date",
            "[class*='time']"
    };

    /**
     * 核心方法: 爬取内容(三级缓存 + 爬虫)
     */
    public XhsContent crawlContent(String url) throws Exception {
        log.info("[爬虫服务-Selenium] 开始爬取: url={}", url);

        // 检查是否是短链接
        boolean isShortLink = XHS_SHORTLINK_PATTERN.matcher(url).find();

        // 短链接场景：先用短链接ID作为postId，爬取后再更新
        String postId;
        if (isShortLink) {
            java.util.regex.Matcher shortMatcher = XHS_SHORTLINK_PATTERN.matcher(url);
            if (shortMatcher.find()) {
                postId = "short_" + shortMatcher.group(1);
                log.info("[爬虫服务-Selenium] 检测到短链接，使用临时postId: {}", postId);
            } else {
                throw new IllegalArgumentException("无法解析短链接ID: " + url);
            }
        } else {
            // 标准链接：直接提取postId
            if (!isValidXhsUrl(url)) {
                throw new IllegalArgumentException("无效的小红书URL: " + url);
            }
            postId = extractPostId(url);
        }

        String cacheKey = CONTENT_CACHE_PREFIX + postId;

        // 第一级缓存: Redis (热数据)
        log.debug("[爬虫服务-Selenium] 检查Redis缓存: postId={}", postId);
        if (redisTemplate != null) {
            XhsContent cachedContent = getFromRedis(cacheKey);
            if (cachedContent != null) {
                log.info("[爬虫服务-Selenium] Redis缓存命中: postId={}, 跳过爬取", postId);
                return cachedContent;
            }
            log.debug("[爬虫服务-Selenium] Redis缓存未命中", postId);
        }

        // 第二级缓存: PostgreSQL (冷数据)
        log.debug("[爬虫服务-Selenium] 检查PostgreSQL数据库: postId={}", postId);
        Optional<XhsContent> dbContent = findContentByUrl(url);
        if (dbContent.isPresent()) {
            XhsContent content = dbContent.get();
            // 验证从数据库读取的内容是否有效
            try {
                validateCrawledContent(content);
                log.info("[爬虫服务-Selenium] PostgreSQL缓存命中且有效: postId={}, 异步更新Redis", postId);
                if (redisTemplate != null) {
                    updateRedisCache(cacheKey, content);
                }
                return content;
            } catch (RuntimeException e) {
                // 数据库中的内容无效，删除并重新爬取
                log.warn("[爬虫服务-Selenium] PostgreSQL缓存内容无效，删除并重新爬取: postId={}, error={}", postId, e.getMessage());
                deleteContent(content);
            }
        }
        log.debug("[爬虫服务-Selenium] PostgreSQL缓存未命中，需要执行爬虫", postId);

        // 第三级: 执行爬虫 (带重试)
        log.info("[爬虫服务-Selenium] 开始Selenium爬取: postId={}", postId);
        XhsContent content = crawlWithRetry(url);

        // 短链接场景：尝试从页面中提取真实postId
        if (isShortLink && content.getPostId() != null && !content.getPostId().startsWith("short_")) {
            String realPostId = content.getPostId();
            log.info("[爬虫服务-Selenium] 短链接已解析，获取真实postId: {} -> {}", postId, realPostId);

            // 检查真实postId是否已存在
            Optional<XhsContent> existingByPostId = findContentByPostId(realPostId);
            if (existingByPostId.isPresent()) {
                log.info("[爬虫服务-Selenium] 真实postId已存在，复用已有记录: {}", realPostId);
                XhsContent existing = existingByPostId.get();
                // 更新URL为短链接（便于追溯来源）
                existing.setUrl(url);
                existing.setUpdatedAt(LocalDateTime.now());
                saveContent(existing);

                // 更新Redis缓存
                String newCacheKey = CONTENT_CACHE_PREFIX + realPostId;
                if (redisTemplate != null) {
                    updateRedisCache(newCacheKey, existing);
                }

                return existing;
            }

            // 更新content的postId
            content.setPostId(realPostId);

            // 用新postId重新保存到数据库
            String newCacheKey = CONTENT_CACHE_PREFIX + realPostId;
            log.debug("[爬虫服务-Selenium] 更新缓存Key: {} -> {}", cacheKey, newCacheKey);

            // 删除旧的短链接记录（如果存在）
            final String oldCacheKey = cacheKey;
            try {
                deleteOldContentByUrl(url, oldCacheKey);
            } catch (Exception e) {
                log.warn("[爬虫服务-Selenium] 删除旧记录失败: {}", e.getMessage());
            }

            // 用新postId保存
            postId = realPostId;
            cacheKey = newCacheKey;
        }

        // 验证爬取内容的有效性
        validateCrawledContent(content);

        // 存储结果
        log.debug("[爬虫服务-Selenium] 同步保存到PostgreSQL: postId={}", postId);
        saveContent(content);

        if (redisTemplate != null) {
            log.debug("[爬虫服务-Selenium] 异步保存到Redis: postId={}", postId);
            updateRedisCache(cacheKey, content);
        }

        log.info("[爬虫服务-Selenium完成] postId={}, title={}", postId, content.getTitle());
        return content;
    }

    /**
     * 带重试的爬虫执行
     */
    private XhsContent crawlWithRetry(String url) throws Exception {
        RuntimeException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.debug("爬虫执行 - 尝试 {}/{}", attempt + 1, MAX_RETRY_ATTEMPTS);
                return executeWebScraping(url);
            } catch (RuntimeException e) {
                lastException = e;
                Throwable cause = e.getCause();
                boolean poolTimeout = e instanceof SeleniumManager.BrowserPoolExhaustedException;
                boolean isRetryable = poolTimeout ||
                        (cause instanceof java.io.IOException) ||
                        (cause instanceof java.net.SocketTimeoutException);

                if (!isRetryable) {
                    log.error("业务异常,不重试: {}", e.getMessage());
                    throw e;
                }

                String reason = poolTimeout ? "浏览器实例池暂时耗尽" : e.getMessage();
                log.warn("爬虫失败(尝试{}/{}): {}", attempt + 1, MAX_RETRY_ATTEMPTS, reason);

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    long delayMs = RETRY_DELAYS_MS[attempt];
                    Thread.sleep(delayMs);
                }
            }
        }

        if (lastException != null) {
            throw new RuntimeException("爬虫失败(已重试" + MAX_RETRY_ATTEMPTS + "次)", lastException);
        }
        throw new RuntimeException("爬虫执行失败");
    }

    /**
     * 执行网页爬虫 - 使用Selenium
     */
    private XhsContent executeWebScraping(String url) {
        long startTime = System.currentTimeMillis();
        SeleniumManager.DriverWrapper driverWrapper = null;

        try {
            driverWrapper = seleniumManager.borrowDriver();
            WebDriver driver = driverWrapper.driver;

            log.debug("[爬虫服务-Selenium] 开始导航到页面: {}", url);
            driver.get(url);

            // 等待主要内容加载完成
            waitForContentLoad(driver);

            // 获取最终URL（短链接跳转后的真实URL）
            String finalUrl = driver.getCurrentUrl();
            log.debug("[爬虫服务-Selenium] 页面URL: {} -> {}", url, finalUrl);

            // 模拟真实用户浏览：页面滚动机制
            simulateUserScrolling(driver);

            XhsContent content = new XhsContent();
            content.setUrl(finalUrl);
            content.setPostId(extractPostId(finalUrl));
            content.setCrawledAt(LocalDateTime.now());
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());

            // 爬取标题
            String title = extractTextWithFallback(driver, TITLE_SELECTORS);
            if (title != null) {
                content.setTitle(title);
            }

            // 爬取正文
            String description = extractTextWithFallback(driver, DESCRIPTION_SELECTORS);
            if (description != null && !description.isEmpty()) {
                content.setContent(description);
                log.debug("正文提取成功，长度: {}", description.length());
            } else {
                log.warn("正文提取失败，所有选择器都未找到内容");
            }

            // 爬取图片
            List<String> images = extractImageUrlsWithFallback(driver, IMAGE_SELECTORS);
            if (!images.isEmpty()) {
                content.setImages(images);
            }

            // 爬取Tag
            List<String> tags = extractTagsWithFallback(driver, TAG_SELECTORS);
            if (!tags.isEmpty()) {
                content.setTags(tags);
            }

            // 提取元数据
            enrichMetadata(driver, content);

            long duration = System.currentTimeMillis() - startTime;
            log.info("爬虫完成: postId={}, 耗时={}ms", content.getPostId(), duration);

            if (duration > 5000) {
                log.warn("爬虫耗时过长(>5s): {}ms", duration);
            }

            return content;

        } catch (RuntimeException e) {
            // 标记 Driver 为不健康，避免重复使用有问题的连接
            if (driverWrapper != null) {
                driverWrapper.markUnhealthy();
            }
            throw e;
        } catch (Exception e) {
            // 标记 Driver 为不健康，避免重复使用有问题的连接
            if (driverWrapper != null) {
                driverWrapper.markUnhealthy();
            }
            throw new RuntimeException("爬虫执行失败: " + e.getMessage(), e);
        } finally {
            if (driverWrapper != null) {
                try {
                    driverWrapper.close();
                } catch (Exception e) {
                    log.warn("关闭Driver Wrapper异常: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 等待内容加载
     */
    private void waitForContentLoad(WebDriver driver) {
        try {
            // 等待主要内容区域出现
            org.openqa.selenium.support.ui.WebDriverWait wait = new org.openqa.selenium.support.ui.WebDriverWait(driver,
                    java.time.Duration.ofSeconds(CONTENT_LOAD_TIMEOUT_SECONDS));
            wait.until(d -> {
                try {
                    return !d.findElements(By.cssSelector("[class*='content']")).isEmpty();
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            log.warn("等待内容加载超时: {}", e.getMessage());
        }
    }

    /**
     * 提取元数据
     */
    private void enrichMetadata(WebDriver driver, XhsContent content) {
        try {
            Map<String, Object> metadata = new HashMap<>();

            String author = normalizeAuthorName(extractTextWithFallback(driver, AUTHOR_SELECTORS));
            if (author != null) {
                metadata.put("author", author);
                content.setAuthorId(author);
            }

            String publishTime = extractTextWithFallback(driver, PUBLISH_TIME_SELECTORS);
            if (publishTime != null) {
                metadata.put("publishTime", publishTime);
            }

            metadata.put("likes", extractNumber(driver, "[class*='like']"));
            metadata.put("comments", extractNumber(driver, "[class*='comment']"));
            metadata.put("shares", extractNumber(driver, "[class*='share']"));

            content.setMetadata(metadata);
        } catch (Exception e) {
            log.warn("元数据提取异常: {}", e.getMessage());
        }
    }

    /**
     * 提取文本 - 带回退机制，尝试多个选择器
     */
    private String extractTextWithFallback(WebDriver driver, String... selectors) {
        for (String selector : selectors) {
            try {
                log.debug("尝试选择器: {}", selector);
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                log.debug("选择器 {} 匹配到 {} 个元素", selector, elements.size());

                if (!elements.isEmpty()) {
                    String text = elements.get(0).getText().trim();
                    if (text != null && !text.isEmpty()) {
                        log.info("成功提取文本，使用选择器: {}, 长度: {}", selector, text.length());
                        return text;
                    }
                }
            } catch (Exception e) {
                log.debug("选择器 {} 提取失败: {}", selector, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 提取图片URL（精确优先，失败后回退）
     */
    private List<String> extractImageUrlsWithFallback(WebDriver driver, String... selectors) {
        for (String selector : selectors) {
            if (selector == null || selector.isEmpty()) {
                continue;
            }
            List<String> urls = extractImageUrlsBySelector(driver, selector);
            if (!urls.isEmpty()) {
                return urls;
            }
        }
        return Collections.emptyList();
    }

    /**
     * 提取图片URL（过滤掉头像）
     */
    @SuppressWarnings("unchecked")
    private List<String> extractImageUrlsBySelector(WebDriver driver, String selector) {
        List<String> urls = new ArrayList<>();
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                    "return Array.from(document.querySelectorAll(arguments[0]))" +
                            ".map(img => img.src || img.getAttribute('data-src') || img.dataset.src)" +
                            ".filter(Boolean)",
                    selector);

            if (result instanceof java.util.List) {
                for (Object item : (java.util.List<?>) result) {
                    if (item != null) {
                        String url = item.toString();
                        // 过滤掉头像图片
                        if (!url.contains("/avatar/") && !url.contains("avatar")) {
                            urls.add(url);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("图片URL提取失败: {}", e.getMessage());
        }
        return urls;
    }

    /**
     * 提取Tag（精确优先，失败后回退）
     */
    private List<String> extractTagsWithFallback(WebDriver driver, String... selectors) {
        for (String selector : selectors) {
            if (selector == null || selector.isEmpty()) {
                continue;
            }
            List<String> tags = extractTagsBySelector(driver, selector);
            if (!tags.isEmpty()) {
                return tags;
            }
        }
        return Collections.emptyList();
    }

    /**
     * 提取Tag
     */
    @SuppressWarnings("unchecked")
    private List<String> extractTagsBySelector(WebDriver driver, String selector) {
        List<String> tags = new ArrayList<>();
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                    "return Array.from(document.querySelectorAll(arguments[0]))" +
                            ".map(tag => tag.innerText).filter(Boolean)",
                    selector);

            if (result instanceof java.util.List) {
                for (Object item : (java.util.List<?>) result) {
                    if (item != null) {
                        tags.add(item.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Tag提取失败: {}", e.getMessage());
        }
        return tags;
    }

    /**
     * 提取数字
     */
    private Integer extractNumber(WebDriver driver, String selector) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                    "return parseInt(document.querySelector(arguments[0])?.innerText || '0')",
                    selector);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 规范化作者名称
     */
    private String normalizeAuthorName(String rawAuthor) {
        if (rawAuthor == null) {
            return null;
        }
        String cleaned = rawAuthor
                .replace("关注", "")
                .replace("+", "")
                .replace('\n', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 模拟真实用户滚动页面
     */
    private void simulateUserScrolling(WebDriver driver) {
        try {
            log.debug("[爬虫服务-Selenium] 开始模拟用户滚动");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 获取页面总高度
            Long totalHeight = (Long) js.executeScript("return document.body.scrollHeight");

            // 分段滚动
            int currentPosition = 0;
            int scrollCount = 0;

            while (currentPosition < totalHeight && scrollCount < MAX_SCROLLS) {
                int randomScroll = SCROLL_STEP + (int) (Math.random() * SCROLL_RANDOM_RANGE);
                currentPosition += randomScroll;

                js.executeScript("window.scrollTo(0, " + currentPosition + ")");

                // 随机等待
                int waitTime = SCROLL_WAIT_BASE_MS + (int) (Math.random() * SCROLL_WAIT_RANDOM_MS);
                Thread.sleep(waitTime);

                scrollCount++;
                log.debug("[爬虫服务-Selenium] 滚动进度: {}/{}, 位置: {}px", scrollCount, MAX_SCROLLS, currentPosition);
            }

            // 滚动回顶部
            js.executeScript("window.scrollTo(0, 0)");
            Thread.sleep(SCROLL_TO_TOP_WAIT_MS);

            log.debug("[爬虫服务-Selenium] 用户滚动模拟完成");

        } catch (Exception e) {
            log.warn("[爬虫服务-Selenium] 页面滚动失败: {}", e.getMessage());
        }
    }

    /**
     * 验证爬取的内容是否有效
     */
    private void validateCrawledContent(XhsContent content) {
        List<String> errors = new ArrayList<>();

        if (content.getPostId() == null || content.getPostId().isEmpty()) {
            errors.add("PostID为空");
        }

        if (content.getUrl() == null || content.getUrl().isEmpty()) {
            errors.add("URL为空");
        }

        boolean hasTitle = content.getTitle() != null && !content.getTitle().trim().isEmpty();
        boolean hasContent = content.getContent() != null && !content.getContent().trim().isEmpty();
        boolean hasImages = content.getImages() != null && !content.getImages().isEmpty();

        if (!hasTitle && !hasContent && !hasImages) {
            errors.add("爬取内容为空：没有标题、正文或图片");
        }

        if (!errors.isEmpty()) {
            String errorMsg = String.join("; ", errors);
            log.error("[爬虫服务-Selenium] 内容验证失败: postId={}, errors={}", content.getPostId(), errorMsg);
            throw new RuntimeException("爬取内容验证失败: " + errorMsg);
        }

        log.info("[爬虫服务-Selenium] 内容验证通过: postId={}, hasTitle={}, hasContent={}, hasImages={}",
                content.getPostId(), hasTitle, hasContent, hasImages);
    }

    /**
     * 验证URL
     */
    private boolean isValidXhsUrl(String url) {
        return url != null && XHS_URL_PATTERN.matcher(url).find();
    }

    /**
     * 提取Post ID
     */
    private String extractPostId(String url) {
        java.util.regex.Matcher matcher = XHS_URL_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("无法从URL中提取Post ID: " + url);
    }

    /**
     * 从Redis获取缓存
     */
    private XhsContent getFromRedis(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis获取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新Redis缓存
     */
    private void updateRedisCache(String cacheKey, XhsContent content) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, content, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis缓存更新失败: {}", e.getMessage());
        }
    }

    /**
     * 批量爬取（支持并发）
     */
    public List<XhsContent> crawlContentBatch(List<String> urls) {
        List<XhsContent> results = Collections.synchronizedList(new ArrayList<>());
        urls.parallelStream().forEach(url -> {
            try {
                results.add(crawlContent(url));
            } catch (Exception e) {
                log.error("批量爬虫异常 [{}]: {}", url, e.getMessage());
            }
        });
        return results;
    }

    /**
     * 清理缓存
     */
    public void clearCache(String url) {
        try {
            String postId = extractPostId(url);
            String cacheKey = CONTENT_CACHE_PREFIX + postId;
            if (redisTemplate != null) {
                redisTemplate.delete(cacheKey);
                log.info("Redis缓存清理: {}", cacheKey);
            }
        } catch (Exception e) {
            log.warn("缓存清理异常: {}", e.getMessage());
        }
    }

    // ==================== 事务隔离的数据库操作辅助方法 ====================

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    private Optional<XhsContent> findContentByUrl(String url) {
        return contentRepository.findByUrl(url);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    private Optional<XhsContent> findContentByPostId(String postId) {
        return contentRepository.findByPostId(postId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveContent(XhsContent content) {
        contentRepository.save(content);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void deleteContent(XhsContent content) {
        contentRepository.delete(content);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void deleteOldContentByUrl(String url, String cacheKey) {
        contentRepository.findByUrl(url).ifPresent(oldContent -> {
            contentRepository.delete(oldContent);
            if (redisTemplate != null) {
                redisTemplate.delete(cacheKey);
            }
        });
    }
}
