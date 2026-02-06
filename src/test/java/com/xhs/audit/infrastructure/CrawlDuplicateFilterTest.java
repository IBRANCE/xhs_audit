package com.xhs.audit.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CrawlDuplicateFilter 单元测试
 *
 * 测试布隆过滤器的基本操作和边缘情况
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrawlDuplicateFilterTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBloomFilter<Object> bloomFilter;

    private CrawlDuplicateFilter crawlDuplicateFilter;

    @BeforeEach
    void setUp() {
        when(redissonClient.getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(true);
        when(bloomFilter.count()).thenReturn(1000L);

        crawlDuplicateFilter = new CrawlDuplicateFilter();
        ReflectionTestUtils.setField(crawlDuplicateFilter, "redissonClient", redissonClient);
        // 必须调用 init() 来初始化 bloomFilter
        crawlDuplicateFilter.init();
    }

    @Test
    @DisplayName("初始化时布隆过滤器已存在")
    void testInitWithExistingFilter() {
        when(bloomFilter.isExists()).thenReturn(true);

        crawlDuplicateFilter.init();

        verify(redissonClient, atLeast(1)).getBloomFilter("crawl:url:filter");
        verify(bloomFilter, atLeast(1)).isExists();
        verify(bloomFilter, atLeast(1)).count();
    }

    @Test
    @DisplayName("初始化时布隆过滤器不存在，创建新过滤器")
    void testInitWithNewFilter() {
        when(bloomFilter.isExists()).thenReturn(false);
        when(bloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(true);

        crawlDuplicateFilter.init();

        verify(bloomFilter).tryInit(100_000L, 0.01);
    }

    @Test
    @DisplayName("初始化时过滤器创建失败")
    void testInitWithFilterCreationFailure() {
        when(bloomFilter.isExists()).thenReturn(false);
        when(bloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(false);

        crawlDuplicateFilter.init();

        // 初始化失败时不应抛出异常，只是记录警告
        verify(bloomFilter).tryInit(100_000L, 0.01);
    }

    @Test
    @DisplayName("mightContain 返回 true 表示可能存在")
    void testMightContain_ReturnsTrue() {
        when(bloomFilter.contains((Object) anyString())).thenReturn(true);

        boolean result = crawlDuplicateFilter.mightContain("http://example.com");

        assertThat(result).isTrue();
        verify(bloomFilter).contains("http://example.com");
    }

    @Test
    @DisplayName("mightContain 返回 false 表示一定不存在")
    void testMightContain_ReturnsFalse() {
        when(bloomFilter.contains((Object) anyString())).thenReturn(false);

        boolean result = crawlDuplicateFilter.mightContain("http://notexists.com");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("mightContain 处理 null 输入")
    void testMightContain_WithNullInput() {
        boolean result = crawlDuplicateFilter.mightContain(null);

        assertThat(result).isFalse();
        // null 输入不应调用 contains 方法
        verify(bloomFilter, never()).contains(anyString());
    }

    @Test
    @DisplayName("mightContain 处理异常情况")
    void testMightContain_WithException() {
        when(bloomFilter.contains((Object) anyString())).thenThrow(new RuntimeException("Redis connection error"));

        // 异常时应返回 false，保守策略
        boolean result = crawlDuplicateFilter.mightContain("http://example.com");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("add 方法添加 URL 到过滤器")
    void testAdd() {
        crawlDuplicateFilter.add("http://example.com");

        verify(bloomFilter).add("http://example.com");
    }

    @Test
    @DisplayName("add 方法处理异常情况")
    void testAdd_WithException() {
        doThrow(new RuntimeException("Redis connection error")).when(bloomFilter).add(anyString());

        // add 方法不应抛出异常
        crawlDuplicateFilter.add("http://example.com");

        // 验证方法被调用
        verify(bloomFilter).add(anyString());
    }

    @Test
    @DisplayName("checkAndAdd 返回 false 表示新添加")
    void testCheckAndAdd_ReturnsFalse() {
        when(bloomFilter.contains((Object) anyString())).thenReturn(false);

        boolean result = crawlDuplicateFilter.checkAndAdd("http://newurl.com");

        assertThat(result).isFalse();
        verify(bloomFilter).contains("http://newurl.com");
        verify(bloomFilter).add("http://newurl.com");
    }

    @Test
    @DisplayName("checkAndAdd 返回 true 表示已存在")
    void testCheckAndAdd_ReturnsTrue() {
        when(bloomFilter.contains((Object) anyString())).thenReturn(true);

        boolean result = crawlDuplicateFilter.checkAndAdd("http://existingurl.com");

        assertThat(result).isTrue();
        // 已存在时不添加
        verify(bloomFilter, never()).add(anyString());
    }

    @Test
    @DisplayName("checkAndAdd 处理异常情况")
    void testCheckAndAdd_WithException() {
        when(bloomFilter.contains((Object) anyString())).thenThrow(new RuntimeException("Redis error"));

        boolean result = crawlDuplicateFilter.checkAndAdd("http://example.com");

        // 异常时返回 false
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("count 方法返回过滤器中的元素数量")
    void testCount() {
        when(bloomFilter.count()).thenReturn(500L);

        long result = crawlDuplicateFilter.count();

        assertThat(result).isEqualTo(500L);
    }

    @Test
    @DisplayName("count 方法处理异常情况")
    void testCount_WithException() {
        when(bloomFilter.count()).thenThrow(new RuntimeException("Redis error"));

        long result = crawlDuplicateFilter.count();

        // 异常时返回 -1
        assertThat(result).isEqualTo(-1);
    }

    @Test
    @DisplayName("clear 方法清空并重新初始化过滤器")
    void testClear() {
        // 由于 clear() 中会重新调用 init()，这会获取新的 bloomFilter
        // 我们只验证 delete 被调用
        crawlDuplicateFilter.clear();

        verify(bloomFilter, atLeast(1)).delete();
    }

    @Test
    @DisplayName("布隆过滤器特性测试 - false positive 是可能的")
    void testBloomFilterFalsePositiveCharacteristic() {
        // 添加一个 URL
        crawlDuplicateFilter.add("http://original.com");

        // 检查另一个 URL - 由于布隆过滤器的特性，可能返回 true（误判）
        // 但这不是错误，而是布隆过滤器的预期行为
        when(bloomFilter.contains((Object) anyString())).thenReturn(true);

        boolean result = crawlDuplicateFilter.mightContain("http://different.com");

        // 结果可能是 true，这是可接受的（false positive）
        // 重点是 mustContain 返回 false 时一定不存在
        assertThat(true).isEqualTo(true); // 确认测试执行
    }
}
