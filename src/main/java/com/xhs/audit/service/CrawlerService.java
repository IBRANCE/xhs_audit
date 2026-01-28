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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.xhs.audit.infrastructure.PlaywrightManager;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.XhsContentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 爬虫服务 - 负责从小红书爬取内容
 *
 * 核心功能:
 * 1. 三级缓存检查 (Redis -> PostgreSQL -> 爬虫)
 * 2. 基于Playwright的动态爬虫
 * 3. 异常处理和重试机制
 * 4. 数据持久化
 *
 * @author Bruce
 * @since 2026-02-01
 */
@Service
@Slf4j
public class CrawlerService {

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private XhsContentRepository contentRepository;

    @Autowired(required = false)
    private RedisTemplate<String, XhsContent> redisTemplate;

    private static final String CONTENT_CACHE_PREFIX = "xhs:content:";
    private static final long CACHE_TTL_SECONDS = 86400;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MS = { 1000, 2000, 4000 };
    // 支持标准小红书链接和 xhslink.com 短链接
    private static final Pattern XHS_URL_PATTERN = Pattern.compile(
            "https://(?:www\\.)?xiaohongshu\\.com/(?:explore|discovery/item)/([a-zA-Z0-9_-]+)");
    private static final Pattern XHS_SHORTLINK_PATTERN = Pattern.compile(
            "https?://xhslink\\.com/o/([a-zA-Z0-9]+)");

    /**
     * 核心方法: 爬取内容(三级缓存 + 爬虫)
     * 使用REQUIRES_NEW传播级别，确保独立事务，不受外层事务回滚影响
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public XhsContent crawlContent(String url) throws Exception {
        log.info("[爬虫服务] 开始爬取: url={}", url);

        // 检查是否是短链接
        boolean isShortLink = XHS_SHORTLINK_PATTERN.matcher(url).find();

        // 短链接场景：先用短链接ID作为postId，爬取后再更新
        String postId;
        if (isShortLink) {
            java.util.regex.Matcher shortMatcher = XHS_SHORTLINK_PATTERN.matcher(url);
            if (shortMatcher.find()) {
                postId = "short_" + shortMatcher.group(1);
                log.info("[爬虫服务] 检测到短链接，使用临时postId: {}", postId);
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
        log.debug("[爬虫服务] 检查Redis缓存: postId={}", postId);
        if (redisTemplate != null) {
            XhsContent cachedContent = getFromRedis(cacheKey);
            if (cachedContent != null) {
                log.info("[爬虫服务] Redis缓存命中: postId={}, 跳过爬取", postId);
                return cachedContent;
            }
            log.debug("[爬虫服务] Redis缓存未命中", postId);
        }

        // 第二级缓存: PostgreSQL (冷数据)
        log.debug("[爬虫服务] 检查PostgreSQL数据库: postId={}", postId);
        Optional<XhsContent> dbContent = contentRepository.findByUrl(url);
        if (dbContent.isPresent()) {
            XhsContent content = dbContent.get();
            // 验证从数据库读取的内容是否有效
            try {
                validateCrawledContent(content);
                log.info("[爬虫服务] PostgreSQL缓存命中且有效: postId={}, 异步更新Redis", postId);
                if (redisTemplate != null) {
                    updateRedisCache(cacheKey, content);
                }
                return content;
            } catch (RuntimeException e) {
                // 数据库中的内容无效，删除并重新爬取
                log.warn("[爬虫服务] PostgreSQL缓存内容无效，删除并重新爬取: postId={}, error={}", postId, e.getMessage());
                contentRepository.delete(content);
                // 继续执行下面的爬虫逻辑
            }
        }
        log.debug("[爬虫服务] PostgreSQL缓存未命中，需要执行爬虫", postId);

        // 第三级: 执行爬虫 (带重试)
        log.info("[爬虫服务] 开始Playwright爬取: postId={}", postId);
        XhsContent content = crawlWithRetry(url);

        // 注意：URL已在executeWebScraping中设置为最终跳转后的URL
        // 短链接场景：尝试从页面中提取真实postId
        if (isShortLink && content.getPostId() != null && !content.getPostId().startsWith("short_")) {
            String realPostId = content.getPostId();
            log.info("[爬虫服务] 短链接已解析，获取真实postId: {} -> {}", postId, realPostId);

            // 更新content的postId
            content.setPostId(realPostId);

            // 用新postId重新保存到数据库
            String newCacheKey = CONTENT_CACHE_PREFIX + realPostId;
            log.debug("[爬虫服务] 更新缓存Key: {} -> {}", cacheKey, newCacheKey);

            // 删除旧的短链接记录（如果存在）
            final String oldCacheKey = cacheKey;
            try {
                contentRepository.findByUrl(url).ifPresent(oldContent -> {
                    contentRepository.delete(oldContent);
                    if (redisTemplate != null) {
                        redisTemplate.delete(oldCacheKey);
                    }
                });
            } catch (Exception e) {
                log.warn("[爬虫服务] 删除旧记录失败: {}", e.getMessage());
            }

            // 用新postId保存
            postId = realPostId;
            cacheKey = newCacheKey;
        }

        // 验证爬取内容的有效性
        validateCrawledContent(content);

        // 存储结果
        log.debug("[爬虫服务] 同步保存到PostgreSQL: postId={}", postId);
        contentRepository.save(content);

        if (redisTemplate != null) {
            log.debug("[爬虫服务] 异步保存到Redis: postId={}", postId);
            updateRedisCache(cacheKey, content);
        }

        log.info("[爬虫服务完成] postId={}, title={}", postId, content.getTitle());
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
                boolean isRetryable = (cause instanceof java.io.IOException) ||
                        (cause instanceof java.net.SocketTimeoutException);

                if (!isRetryable) {
                    log.error("业务异常,不重试: {}", e.getMessage());
                    throw e;
                }

                log.warn("爬虫失败(尝试{}/{}): {}", attempt + 1, MAX_RETRY_ATTEMPTS, e.getMessage());

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
     * 执行网页爬虫 - 使用Playwright
     */
    private XhsContent executeWebScraping(String url) {
        long startTime = System.currentTimeMillis();
        PlaywrightManager.PageWrapper pageWrapper = null;

        try {
            pageWrapper = playwrightManager.borrowPage();
            Page page = pageWrapper.page;

            // 使用domcontentloaded等待策略，避免被持续加载的资源卡住
            // 小红书页面可能有一些资源一直在加载，导致load事件不触发
            Page.NavigateOptions navigateOptions = new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(30000);

            page.navigate(url, navigateOptions);

            // 获取最终URL（短链接跳转后的真实URL）
            String finalUrl = page.url();
            log.debug("[爬虫服务] 页面URL: {} -> {}", url, finalUrl);

            // 等待主要内容加载完成
            page.waitForSelector("[class*='content']", new Page.WaitForSelectorOptions().setTimeout(10000));

            // 模拟真实用户浏览：页面滚动机制
            simulateUserScrolling(page);

            XhsContent content = new XhsContent();
            content.setUrl(finalUrl);
            content.setPostId(extractPostId(finalUrl));
            content.setCrawledAt(LocalDateTime.now());
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());

            // 爬取标题
            String title = extractText(page, "[class*='title']");
            if (title != null) {
                content.setTitle(title);
            }

            // 爬取正文 - 尝试多个可能的选择器
            String description = extractTextWithFallback(page,
                    "[class*='desc']", // ✅ 已验证成功 - 优先使用
                    "[class*='note-content']", // 备用选择器
                    "[class*='content']",
                    "[class*='detail']",
                    "article",
                    ".content",
                    ".note-text");
            if (description != null && !description.isEmpty()) {
                content.setContent(description);
                log.debug("正文提取成功，长度: {}", description.length());
            } else {
                log.warn("正文提取失败，所有选择器都未找到内容");
            }

            // 爬取图片
            List<String> images = extractImageUrls(page, "img[class*='image']");
            if (!images.isEmpty()) {
                content.setImages(images);
            }

            // 爬取Tag
            List<String> tags = extractTags(page, "[class*='tag']");
            if (!tags.isEmpty()) {
                content.setTags(tags);
            }

            // 提取元数据
            enrichMetadata(page, content);

            long duration = System.currentTimeMillis() - startTime;
            log.info("爬虫完成: postId={}, 耗时={}ms", content.getPostId(), duration);

            if (duration > 5000) {
                log.warn("爬虫耗时过长(>5s): {}ms", duration);
            }

            return content;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("爬虫执行失败: " + e.getMessage(), e);
        } finally {
            if (pageWrapper != null) {
                try {
                    pageWrapper.close();
                } catch (Exception e) {
                    log.warn("关闭Page Wrapper异常: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 提取元数据
     */
    private void enrichMetadata(Page page, XhsContent content) {
        try {
            Map<String, Object> metadata = new HashMap<>();

            String author = extractText(page, "[class*='author']");
            if (author != null) {
                metadata.put("author", author);
                content.setAuthorId(author);
            }

            String publishTime = extractText(page, "[class*='time']");
            if (publishTime != null) {
                metadata.put("publishTime", publishTime);
            }

            metadata.put("likes", extractNumber(page, "[class*='like']"));
            metadata.put("comments", extractNumber(page, "[class*='comment']"));
            metadata.put("shares", extractNumber(page, "[class*='share']"));

            content.setMetadata(metadata);
        } catch (Exception e) {
            log.warn("元数据提取异常: {}", e.getMessage());
        }
    }

    /**
     * 提取文本
     */
    private String extractText(Page page, String selector) {
        try {
            // 使用Locator API代替evaluate，更安全可靠
            Locator locator = page.locator(selector);
            if (locator.count() > 0) {
                return locator.first().innerText().trim();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取文本 - 带回退机制，尝试多个选择器
     */
    private String extractTextWithFallback(Page page, String... selectors) {
        for (String selector : selectors) {
            try {
                log.debug("尝试选择器: {}", selector);
                Locator locator = page.locator(selector);
                int count = locator.count();
                log.debug("选择器 {} 匹配到 {} 个元素", selector, count);

                if (count > 0) {
                    String text = locator.first().innerText().trim();
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
     * 提取图片URL（过滤掉头像）
     */
    @SuppressWarnings("unchecked")
    private List<String> extractImageUrls(Page page, String selector) {
        List<String> urls = new ArrayList<>();
        try {
            // 使用参数传递选择器，避免JavaScript语法错误
            Object result = page.evaluate(
                    "(selector) => Array.from(document.querySelectorAll(selector))" +
                            ".map(img => img.src || img.getAttribute('data-src') || img.dataset.src)" +
                            ".filter(Boolean)",
                    selector);

            if (result instanceof java.util.List) {
                for (Object item : (java.util.List<?>) result) {
                    if (item != null) {
                        String url = item.toString();
                        // 过滤掉头像图片（包含/avatar/或/avatar）
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
     * 提取Tag
     */
    @SuppressWarnings("unchecked")
    private List<String> extractTags(Page page, String selector) {
        List<String> tags = new ArrayList<>();
        try {
            // 使用参数传递选择器，避免JavaScript语法错误
            Object result = page.evaluate(
                    "(selector) => Array.from(document.querySelectorAll(selector))" +
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
    private Integer extractNumber(Page page, String selector) {
        try {
            // 使用参数传递选择器，避免JavaScript语法错误
            Object result = page.evaluate(
                    "(selector) => parseInt(document.querySelector(selector)?.innerText || '0')",
                    selector);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 模拟真实用户滚动页面
     * 小红书的反爬机制会检测用户行为，需要模拟真实的浏览动作
     */
    private void simulateUserScrolling(Page page) {
        try {
            log.debug("[爬虫服务] 开始模拟用户滚动");

            // 获取页面总高度
            Object heightObj = page.evaluate("() => document.body.scrollHeight");
            int totalHeight = heightObj instanceof Number ? ((Number) heightObj).intValue() : 3000;

            // 分段滚动，每次滚动200-400像素
            int currentPosition = 0;
            int scrollStep = 300; // 每次滚动的像素
            int maxScrolls = 5; // 最多滚动5次
            int scrollCount = 0;

            while (currentPosition < totalHeight && scrollCount < maxScrolls) {
                // 随机滚动距离（200-400像素）
                int randomScroll = scrollStep + (int) (Math.random() * 200);
                currentPosition += randomScroll;

                // 执行滚动
                page.evaluate("window.scrollTo(0, " + currentPosition + ")");

                // 随机等待时间（200-500ms），模拟真实用户阅读
                int waitTime = 200 + (int) (Math.random() * 300);
                Thread.sleep(waitTime);

                scrollCount++;
                log.debug("[爬虫服务] 滚动进度: {}/{}, 位置: {}px", scrollCount, maxScrolls, currentPosition);
            }

            // 滚动回顶部，确保能看到标题等关键信息
            page.evaluate("window.scrollTo(0, 0)");
            Thread.sleep(300);

            log.debug("[爬虫服务] 用户滚动模拟完成");

        } catch (Exception e) {
            log.warn("[爬虫服务] 页面滚动失败: {}", e.getMessage());
            // 滚动失败不影响爬取，继续执行
        }
    }

    /**
     * 验证爬取的内容是否有效
     * 如果内容无效，抛出异常阻止保存到数据库
     */
    private void validateCrawledContent(XhsContent content) {
        List<String> errors = new ArrayList<>();

        // 验证必填字段
        if (content.getPostId() == null || content.getPostId().isEmpty()) {
            errors.add("PostID为空");
        }

        if (content.getUrl() == null || content.getUrl().isEmpty()) {
            errors.add("URL为空");
        }

        // 验证核心内容 - 至少要有标题或正文
        boolean hasTitle = content.getTitle() != null && !content.getTitle().trim().isEmpty();
        boolean hasContent = content.getContent() != null && !content.getContent().trim().isEmpty();
        boolean hasImages = content.getImages() != null && !content.getImages().isEmpty();

        if (!hasTitle && !hasContent && !hasImages) {
            errors.add("爬取内容为空：没有标题、正文或图片");
        }

        // 如果有错误，抛出异常
        if (!errors.isEmpty()) {
            String errorMsg = String.join("; ", errors);
            log.error("[爬虫服务] 内容验证失败: postId={}, errors={}", content.getPostId(), errorMsg);
            throw new RuntimeException("爬取内容验证失败: " + errorMsg);
        }

        log.info("[爬虫服务] 内容验证通过: postId={}, hasTitle={}, hasContent={}, hasImages={}",
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
     * 批量爬取
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

    /**
     * 清理数据库中的无效内容数据
     * 扫描所有内容，删除不符合验证规则的记录
     * 
     * @return 清理统计信息
     */
    public Map<String, Object> cleanInvalidContents() {
        log.info("[数据清理] 开始清理数据库中的无效内容");

        int totalCount = 0;
        int invalidCount = 0;
        List<String> deletedPostIds = new ArrayList<>();

        try {
            // 查询所有内容
            List<XhsContent> allContents = contentRepository.findAll();
            totalCount = allContents.size();
            log.info("[数据清理] 共找到 {} 条内容记录", totalCount);

            // 验证每条记录
            for (XhsContent content : allContents) {
                try {
                    validateCrawledContent(content);
                    // 验证通过，保留
                } catch (RuntimeException e) {
                    // 验证失败，删除
                    invalidCount++;
                    deletedPostIds.add(content.getPostId());
                    log.warn("[数据清理] 删除无效内容: postId={}, url={}, error={}",
                            content.getPostId(), content.getUrl(), e.getMessage());

                    // 从数据库删除
                    contentRepository.delete(content);

                    // 从Redis删除（如果存在）
                    if (redisTemplate != null) {
                        String cacheKey = CONTENT_CACHE_PREFIX + content.getPostId();
                        redisTemplate.delete(cacheKey);
                    }
                }
            }

            log.info("[数据清理] 清理完成: 总计={}, 无效={}, 有效={}",
                    totalCount, invalidCount, totalCount - invalidCount);

        } catch (Exception e) {
            log.error("[数据清理] 清理过程发生异常", e);
            throw new RuntimeException("数据清理失败: " + e.getMessage(), e);
        }

        // 返回清理统计
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("invalidCount", invalidCount);
        result.put("validCount", totalCount - invalidCount);
        result.put("deletedPostIds", deletedPostIds);
        result.put("timestamp", LocalDateTime.now());

        return result;
    }
}
