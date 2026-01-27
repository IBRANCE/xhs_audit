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
    private static final Pattern XHS_URL_PATTERN = Pattern.compile(
            "https://(?:www\\.)?xiaohongshu\\.com/discover/([a-zA-Z0-9]+)");

    /**
     * 核心方法: 爬取内容(三级缓存 + 爬虫)
     */
    public XhsContent crawlContent(String url) throws Exception {
        log.info("开始爬取内容: {}", url);

        if (!isValidXhsUrl(url)) {
            throw new IllegalArgumentException("无效的小红书URL: " + url);
        }

        String postId = extractPostId(url);
        String cacheKey = CONTENT_CACHE_PREFIX + postId;

        // 第一级缓存: Redis (热数据)
        if (redisTemplate != null) {
            XhsContent cachedContent = getFromRedis(cacheKey);
            if (cachedContent != null) {
                log.debug("Redis缓存命中: postId={}", postId);
                return cachedContent;
            }
        }

        // 第二级缓存: PostgreSQL (冷数据)
        Optional<XhsContent> dbContent = contentRepository.findByUrl(url);
        if (dbContent.isPresent()) {
            XhsContent content = dbContent.get();
            log.debug("PostgreSQL缓存命中: postId={}", postId);
            if (redisTemplate != null) {
                updateRedisCache(cacheKey, content);
            }
            return content;
        }

        // 第三级: 执行爬虫 (带重试)
        XhsContent content = crawlWithRetry(url);

        // 存储结果
        if (redisTemplate != null) {
            updateRedisCache(cacheKey, content);
        }
        contentRepository.save(content);

        log.info("内容爬取成功: postId={}", postId);
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

            page.navigate(url);
            page.waitForSelector("[class*='content']", new Page.WaitForSelectorOptions().setTimeout(10000));

            XhsContent content = new XhsContent();
            content.setUrl(url);
            content.setPostId(extractPostId(url));
            content.setCrawledAt(LocalDateTime.now());
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());

            // 爬取标题
            String title = extractText(page, "[class*='title']");
            if (title != null) {
                content.setTitle(title);
            }

            // 爬取正文
            String description = extractText(page, "[class*='content'], [class*='description']");
            if (description != null) {
                content.setContent(description);
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
            Object result = page.evaluate("() => document.querySelector('" + selector + "')?.innerText");
            return result != null ? result.toString().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取图片URL
     */
    @SuppressWarnings("unchecked")
    private List<String> extractImageUrls(Page page, String selector) {
        List<String> urls = new ArrayList<>();
        try {
            Object result = page.evaluate(
                    "() => Array.from(document.querySelectorAll('" + selector +
                            "')).map(img => img.src || img.data-src)");

            if (result instanceof java.util.List) {
                for (Object item : (java.util.List<?>) result) {
                    if (item != null) {
                        urls.add(item.toString());
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
            Object result = page.evaluate(
                    "() => Array.from(document.querySelectorAll('" + selector +
                            "')).map(tag => tag.innerText)");

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
            Object result = page.evaluate(
                    "() => parseInt(document.querySelector('" + selector + "')?.innerText || '0')");
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
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
}
