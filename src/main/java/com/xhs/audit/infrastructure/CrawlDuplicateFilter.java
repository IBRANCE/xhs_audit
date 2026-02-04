package com.xhs.audit.infrastructure;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * URL 去重过滤器（基于布隆过滤器）
 * 用于多节点部署时快速判断 URL 是否已被爬取
 * 
 * 使用场景：
 * 1. 防止重复爬取（性能优化）
 * 2. 减少数据库查询压力
 * 3. 快速过滤已处理的 URL
 * 
 * 注意事项：
 * 1. 布隆过滤器有误判率（false positive），但不会漏判（no false negative）
 * 2. 如果返回 true（已存在），可能是误判，需要数据库二次确认
 * 3. 如果返回 false（不存在），则一定不存在
 * 
 * @author XHS Audit System
 * @since 2026-02-03 v4.0
 */
@Component
@ConditionalOnProperty(prefix = "audit.bloom-filter", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class CrawlDuplicateFilter {

    @Autowired
    private RedissonClient redissonClient;

    private static final String BLOOM_FILTER_NAME = "crawl:url:filter";

    // 预期元素数量（10 万条）
    private static final long EXPECTED_INSERTIONS = 100_000L;

    // 误判率（1%）
    private static final double FALSE_POSITIVE_PROBABILITY = 0.01;

    private RBloomFilter<String> bloomFilter;

    /**
     * 初始化布隆过滤器
     */
    @PostConstruct
    public void init() {
        try {
            bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);

            // 如果过滤器未初始化，则初始化
            if (!bloomFilter.isExists()) {
                boolean initialized = bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_PROBABILITY);
                if (initialized) {
                    log.info("[BloomFilter] 初始化成功: expectedSize={}, falsePositiveRate={}",
                            EXPECTED_INSERTIONS, FALSE_POSITIVE_PROBABILITY);
                } else {
                    log.warn("[BloomFilter] 初始化失败，可能已被其他节点初始化");
                }
            } else {
                log.info("[BloomFilter] 已存在，跳过初始化: size={}", bloomFilter.count());
            }
        } catch (Exception e) {
            log.error("[BloomFilter] 初始化失败", e);
            throw new IllegalStateException("布隆过滤器初始化失败", e);
        }
    }

    /**
     * 检查 URL 是否可能已被爬取
     * 
     * @param url 待检查的 URL
     * @return true: 可能已爬取（需要数据库二次确认）
     *         false: 一定未爬取
     */
    public boolean mightContain(String url) {
        try {
            boolean contains = bloomFilter.contains(url);
            if (contains) {
                log.debug("[BloomFilter] URL 可能已存在（需二次确认）: {}", url);
            }
            return contains;
        } catch (Exception e) {
            log.error("[BloomFilter] 检查失败: url={}", url, e);
            // 失败时返回 false，让后续流程继续（保守策略）
            return false;
        }
    }

    /**
     * 标记 URL 已被爬取
     * 
     * @param url 已爬取的 URL
     */
    public void add(String url) {
        try {
            bloomFilter.add(url);
            log.debug("[BloomFilter] URL 已标记: {}", url);
        } catch (Exception e) {
            log.error("[BloomFilter] 添加失败: url={}", url, e);
        }
    }

    /**
     * 检查并添加（原子操作）
     * 
     * @param url 待检查并添加的 URL
     * @return true: URL 已存在（可能误判）
     *         false: URL 不存在，已添加
     */
    public boolean checkAndAdd(String url) {
        try {
            boolean existed = bloomFilter.contains(url);
            if (!existed) {
                bloomFilter.add(url);
                log.debug("[BloomFilter] URL 不存在，已添加: {}", url);
            } else {
                log.debug("[BloomFilter] URL 可能已存在: {}", url);
            }
            return existed;
        } catch (Exception e) {
            log.error("[BloomFilter] 检查并添加失败: url={}", url, e);
            return false;
        }
    }

    /**
     * 获取过滤器中的元素数量（近似值）
     */
    public long count() {
        try {
            return bloomFilter.count();
        } catch (Exception e) {
            log.error("[BloomFilter] 获取计数失败", e);
            return -1;
        }
    }

    /**
     * 清空过滤器（谨慎使用）
     */
    public void clear() {
        try {
            bloomFilter.delete();
            init(); // 重新初始化
            log.warn("[BloomFilter] 已清空并重新初始化");
        } catch (Exception e) {
            log.error("[BloomFilter] 清空失败", e);
        }
    }

    /**
     * 轮转布隆过滤器（定期清理，防止内存无限增长）
     * 每天凌晨 2 点执行
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * ?")
    public void rotateBloomFilter() {
        try {
            log.info("[BloomFilter] 开始轮转过滤器");

            String oldFilterName = BLOOM_FILTER_NAME + ":old";
            long oldCount = bloomFilter.count();

            // 1. 删除旧的备份过滤器（如果存在）
            RBloomFilter<String> oldBackup = redissonClient.getBloomFilter(oldFilterName);
            if (oldBackup.isExists()) {
                oldBackup.delete();
                log.info("[BloomFilter] 删除旧备份过滤器");
            }

            // 2. 将当前过滤器重命名为备份
            redissonClient.getKeys().rename(BLOOM_FILTER_NAME, oldFilterName);
            log.info("[BloomFilter] 当前过滤器已重命名为备份: {} 条记录", oldCount);

            // 3. 初始化新过滤器
            init();
            log.info("[BloomFilter] 新过滤器已初始化");

            // 4. 设置备份过滤器 7 天后过期
            oldBackup = redissonClient.getBloomFilter(oldFilterName);
            oldBackup.expire(java.time.Duration.ofDays(7));
            log.info("[BloomFilter] 备份过滤器将在 7 天后过期");

            log.info("[BloomFilter] 轮转完成: 旧过滤器 {} 条，新过滤器 0 条", oldCount);

        } catch (Exception e) {
            log.error("[BloomFilter] 轮转失败", e);
        }
    }
}
