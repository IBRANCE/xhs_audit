package com.xhs.audit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostIdExtractor 单元测试
 * 测试小红书 postId 提取功能
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@DisplayName("PostIdExtractor - 小红书 PostID 提取工具测试")
class PostIdExtractorTest {

    @Nested
    @DisplayName("extract 方法 - 正常场景")
    class ExtractHappyPath {

        @Test
        @DisplayName("标准 explore URL 格式")
        void testExtractExploreUrl() {
            String url = "https://www.xiaohongshu.com/explore/64abc1234567890def";
            String result = PostIdExtractor.extract(url);
            assertEquals("64abc1234567890def", result);
        }

        @Test
        @DisplayName("标准 discovery/item URL 格式")
        void testExtractDiscoveryItemUrl() {
            String url = "https://www.xiaohongshu.com/discovery/item/64abc1234567890def";
            String result = PostIdExtractor.extract(url);
            assertEquals("64abc1234567890def", result);
        }

        @Test
        @DisplayName("短链接格式 xhslink.com/o/")
        void testExtractShortLink() {
            String url = "https://xhslink.com/o/abc123def456";
            String result = PostIdExtractor.extract(url);
            assertEquals("short_abc123def456", result);
        }

        @Test
        @DisplayName("URL 包含查询参数")
        void testExtractUrlWithQueryParams() {
            String url = "https://www.xiaohongshu.com/explore/64abc1234567890def?share_id=xyz";
            String result = PostIdExtractor.extract(url);
            assertEquals("64abc1234567890def", result);
        }

        @Test
        @DisplayName("URL 包含特殊字符的 postId")
        void testExtractPostIdWithSpecialChars() {
            String url = "https://www.xiaohongshu.com/explore/test-post_123-abc";
            String result = PostIdExtractor.extract(url);
            assertEquals("test-post_123-abc", result);
        }
    }

    @Nested
    @DisplayName("extract 方法 - 异常场景")
    class ExtractExceptionCases {

        @Test
        @DisplayName("null 输入应抛出 IllegalArgumentException")
        void testExtractNullUrl() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> PostIdExtractor.extract(null)
            );
            assertEquals("URL 不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("空字符串输入应抛出 IllegalArgumentException")
        void testExtractEmptyUrl() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> PostIdExtractor.extract("")
            );
            assertEquals("URL 不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("无效 URL 应抛出 IllegalArgumentException")
        void testExtractInvalidUrl() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> PostIdExtractor.extract("https://www.example.com/page/123")
            );
            assertTrue(exception.getMessage().contains("无法从 URL 中提取 postId"));
        }

        @Test
        @DisplayName("非小红书 URL 应抛出异常")
        void testExtractNonXiaohongshuUrl() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PostIdExtractor.extract("https://www.weibo.com/123")
            );
        }
    }

    @Nested
    @DisplayName("extractSafe 方法 - 安全提取")
    class ExtractSafeTests {

        @Test
        @DisplayName("有效 URL 应返回 postId")
        void testExtractSafeValidUrl() {
            String url = "https://www.xiaohongshu.com/explore/64abc1234567890def";
            String result = PostIdExtractor.extractSafe(url);
            assertEquals("64abc1234567890def", result);
        }

        @Test
        @DisplayName("无效 URL 应返回 null")
        void testExtractSafeInvalidUrl() {
            String result = PostIdExtractor.extractSafe("https://www.example.com");
            assertNull(result);
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void testExtractSafeNull() {
            String result = PostIdExtractor.extractSafe(null);
            assertNull(result);
        }

        @Test
        @DisplayName("空字符串输入应返回 null")
        void testExtractSafeEmpty() {
            String result = PostIdExtractor.extractSafe("");
            assertNull(result);
        }

        @Test
        @DisplayName("短链接安全提取应返回带前缀的 ID")
        void testExtractSafeShortLink() {
            String url = "https://xhslink.com/o/abc123";
            String result = PostIdExtractor.extractSafe(url);
            assertEquals("short_abc123", result);
        }
    }

    @Nested
    @DisplayName("isValid 方法 - URL 校验")
    class IsValidTests {

        @Test
        @DisplayName("有效的 explore URL 应返回 true")
        void testIsValidExploreUrl() {
            assertTrue(PostIdExtractor.isValid("https://www.xiaohongshu.com/explore/123abc"));
        }

        @Test
        @DisplayName("有效的 discovery/item URL 应返回 true")
        void testIsValidDiscoveryItemUrl() {
            assertTrue(PostIdExtractor.isValid("https://www.xiaohongshu.com/discovery/item/123abc"));
        }

        @Test
        @DisplayName("有效的短链接应返回 true")
        void testIsValidShortLink() {
            assertTrue(PostIdExtractor.isValid("https://xhslink.com/o/abc123"));
        }

        @Test
        @DisplayName("null 输入应返回 false")
        void testIsValidNull() {
            assertFalse(PostIdExtractor.isValid(null));
        }

        @Test
        @DisplayName("空字符串应返回 false")
        void testIsValidEmpty() {
            assertFalse(PostIdExtractor.isValid(""));
        }

        @Test
        @DisplayName("无效 URL 应返回 false")
        void testIsValidInvalidUrl() {
            assertFalse(PostIdExtractor.isValid("https://www.example.com"));
        }
    }

    @Nested
    @DisplayName("isShortLink 方法 - 短链接判断")
    class IsShortLinkTests {

        @Test
        @DisplayName("短链接应返回 true")
        void testIsShortLinkTrue() {
            assertTrue(PostIdExtractor.isShortLink("https://xhslink.com/o/abc123"));
            assertTrue(PostIdExtractor.isShortLink("http://xhslink.com/o/abc123XYZ"));
        }

        @Test
        @DisplayName("标准 URL 应返回 false")
        void testIsShortLinkFalse() {
            assertFalse(PostIdExtractor.isShortLink("https://www.xiaohongshu.com/explore/123abc"));
            assertFalse(PostIdExtractor.isShortLink("https://www.xiaohongshu.com/discovery/item/123abc"));
        }

        @Test
        @DisplayName("null 输入应返回 false")
        void testIsShortLinkNull() {
            assertFalse(PostIdExtractor.isShortLink(null));
        }

        @Test
        @DisplayName("空字符串应返回 false")
        void testIsShortLinkEmpty() {
            assertFalse(PostIdExtractor.isShortLink(""));
        }

        @Test
        @DisplayName("不包含短链接模式的 URL 应返回 false")
        void testIsShortLinkNoPattern() {
            assertFalse(PostIdExtractor.isShortLink("https://www.example.com/o/abc123"));
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCases {

        @Test
        @DisplayName("仅包含域名的 URL 应抛出异常")
        void testExtractOnlyDomain() {
            assertThrows(IllegalArgumentException.class,
                    () -> PostIdExtractor.extract("https://www.xiaohongshu.com"));
        }

        @Test
        @DisplayName("URL 路径不匹配应抛出异常")
        void testExtractNoMatchingPath() {
            assertThrows(IllegalArgumentException.class,
                    () -> PostIdExtractor.extract("https://www.xiaohongshu.com/profile/123"));
        }

        @Test
        @DisplayName("带锚点的 URL 应正常提取")
        void testExtractWithAnchor() {
            String url = "https://www.xiaohongshu.com/explore/123abc#comment";
            String result = PostIdExtractor.extract(url);
            assertEquals("123abc", result);
        }

        @Test
        @DisplayName("URL 编码的字符应正常处理")
        void testExtractUrlEncoded() {
            // postId 本身不包含需要编码的字符，但测试确保 URL 编码不影响提取
            String url = "https://www.xiaohongshu.com/explore/test%2Dpost_123";
            String result = PostIdExtractor.extract(url);
            assertEquals("test%2Dpost_123", result);
        }
    }
}
