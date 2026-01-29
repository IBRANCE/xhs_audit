package com.xhs.audit.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小红书 postId 提取工具
 *
 * 支持以下 URL 格式:
 * - https://www.xiaohongshu.com/explore/{postId}
 * - https://www.xiaohongshu.com/discovery/item/{postId}
 * - https://xhslink.com/o/{shortId}
 *
 * @author XHS Audit System
 * @since 2026-01-29
 */
public final class PostIdExtractor {

    /**
     * 标准链接模式: /explore/{postId} 或 /discovery/item/{postId}
     */
    private static final Pattern POST_ID_PATTERN = Pattern.compile(
            "/(explore|discovery/item)/([a-zA-Z0-9_-]+)");

    /**
     * 短链接模式: xhslink.com/o/{shortId}
     */
    private static final Pattern SHORT_LINK_PATTERN = Pattern.compile(
            "xhslink\\.com/o/([a-zA-Z0-9]+)");

    private PostIdExtractor() {
        // 工具类禁止实例化
    }

    /**
     * 从 URL 中提取 postId
     *
     * @param url 小红书链接
     * @return postId
     * @throws IllegalArgumentException 如果无法从 URL 中提取 postId
     */
    public static String extract(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL 不能为空");
        }

        // 优先尝试标准链接格式
        Matcher matcher = POST_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(2);
        }

        // 短链接格式
        Matcher shortMatcher = SHORT_LINK_PATTERN.matcher(url);
        if (shortMatcher.find()) {
            return "short_" + shortMatcher.group(1);
        }

        throw new IllegalArgumentException("无法从 URL 中提取 postId: " + url);
    }

    /**
     * 从 URL 中提取 postId（不抛异常）
     *
     * @param url 小红书链接
     * @return postId，如果无法提取返回 null
     */
    public static String extractSafe(String url) {
        try {
            return extract(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 检查是否为有效的小红书 URL
     *
     * @param url URL
     * @return 是否为有效的小红书 URL
     */
    public static boolean isValid(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return POST_ID_PATTERN.matcher(url).find() || SHORT_LINK_PATTERN.matcher(url).find();
    }

    /**
     * 检查是否为短链接
     *
     * @param url URL
     * @return 是否为短链接
     */
    public static boolean isShortLink(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return SHORT_LINK_PATTERN.matcher(url).find();
    }
}
