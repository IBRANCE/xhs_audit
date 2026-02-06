package com.xhs.audit.model.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小红书内容实体
 * <p>
 * 存储从爬虫获取的小红书笔记内容，用于后续的AI审核。
 * <p>
 * 数据来源：由 {@link com.xhs.audit.service.CrawlerService} 通过Selenium爬取后存入
 * <p>
 * 字段设计说明：
 * <ul>
 *   <li>核心内容：postId, url, title, content</li>
 *   <li>媒体信息：images, tags</li>
 *   <li>作者信息：authorId</li>
 *   <li>时间信息：publishedAt, crawledAt</li>
 *   <li>扩展信息：metadata(JSON格式)</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Entity
@Table(name = "xhs_content", indexes = {
        @Index(name = "idx_post_id", columnList = "post_id"),
        @Index(name = "idx_author_id", columnList = "author_id"),
        @Index(name = "idx_crawled_at", columnList = "crawled_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XhsContent {

    /**
     * 数据库主键，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 小红书笔记ID
     * <p>
     * 格式：24位字母数字组合，如 "6598cd1b00000000001c3d4c"
     * 唯一标识一篇笔记，用于关联审核结果
     */
    @Column(nullable = false, unique = true, length = 256)
    private String postId;

    /**
     * 笔记原文URL
     * <p>
     * 格式：https://www.xiaohongshu.com/explore/{postId}
     * 或短链接：https://xhslink.com/o/{shortId}
     */
    @Column(nullable = false, length = 500)
    private String url;

    /**
     * 笔记标题
     * <p>
     * 从页面meta标签或标题元素提取
     */
    @Column(length = 500)
    private String title;

    /**
     * 笔记正文内容
     * <p>
     * 包含笔记的完整文本内容，用于AI审核
     * 使用TEXT类型存储长文本
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 图片URL列表
     * <p>
     * JSON数组格式，存储笔记中所有图片的URL
     * 示例：["https://img.example.com/1.jpg", "https://img.example.com/2.jpg"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> images;

    /**
     * 笔记标签列表
     * <p>
     * JSON数组格式，存储笔记的所有标签
     * 示例：["护肤", "美白", "学生党"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tags;

    /**
     * 扩展元数据
     * <p>
     * JSON对象格式，存储其他扩展信息
     * 可能的字段：
     * <ul>
     *   <li>likedCount - 点赞数</li>
     *   <li>collectedCount - 收藏数</li>
     *   <li>commentCount - 评论数</li>
     *   <li>shareCount - 分享数</li>
     *   <li>authorName - 作者昵称</li>
     *   <li>authorAvatar - 作者头像</li>
     * </ul>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    /**
     * 作者ID
     * <p>
     * 小红书用户ID，用于标识内容创作者
     */
    @Column(length = 256)
    private String authorId;

    /**
     * 笔记发布时间
     * <p>
     * 从页面发布时间元素提取，可能为空（未公开或无法获取）
     */
    private LocalDateTime publishedAt;

    /**
     * 内容爬取时间
     * <p>
     * 记录内容被系统爬取的时间，用于缓存管理和去重
     */
    @Column(nullable = false)
    private LocalDateTime crawledAt = LocalDateTime.now();

    /**
     * 数据库记录创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 数据库记录更新时间
     * <p>
     * 由JPA @PreUpdate自动维护
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * JPA实体更新前回调
     * <p>
     * 自动更新updatedAt字段
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
