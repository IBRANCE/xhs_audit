package com.xhs.audit.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.xhs.audit.model.entity.XhsContent;

/**
 * 小红书内容数据访问接口
 * <p>
 * 提供 {@link XhsContent} 实体类的CRUD和查询操作。
 * <p>
 * 查询分类：
 * <ul>
 *   <li>主键查询：findByPostId, findByUrl</li>
 *   <li>作者查询：findByAuthorIdOrderByPublishedAtDesc</li>
 *   <li>缓存查询：existsRecentlyCrawled</li>
 *   <li>批量查询：findByPostIdIn</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Repository
public interface XhsContentRepository extends JpaRepository<XhsContent, Long> {

    /**
     * 根据帖子ID查询内容
     * <p>
     * 使用唯一postId查询，支持缓存命中
     *
     * @param postId 帖子ID
     * @return 内容实体
     */
    Optional<XhsContent> findByPostId(String postId);

    /**
     * 根据URL查询内容
     * <p>
     * 用于URL去重和缓存检查
     *
     * @param url 帖子URL
     * @return 内容实体
     */
    Optional<XhsContent> findByUrl(String url);

    /**
     * 根据作者ID查询内容列表
     * <p>
     * 按发布时间倒序，查看作者的所有笔记
     *
     * @param authorId 作者ID
     * @return 内容列表
     */
    List<XhsContent> findByAuthorIdOrderByPublishedAtDesc(String authorId);

    /**
     * 检查内容是否在最近爬取过
     * <p>
     * 用于缓存策略，避免重复爬取
     *
     * @param postId   帖子ID
     * @param threshold 时间阈值
     * @return true-最近爬取过，false-未爬取或已过期
     */
    @Query("SELECT COUNT(c) > 0 FROM XhsContent c WHERE c.postId = :postId AND c.crawledAt > :threshold")
    boolean existsRecentlyCrawled(@Param("postId") String postId, @Param("threshold") LocalDateTime threshold);

    /**
     * 批量根据帖子ID查询内容
     * <p>
     * 用于批量任务处理
     *
     * @param postIds 帖子ID列表
     * @return 内容列表
     */
    @Query("SELECT c FROM XhsContent c WHERE c.postId IN :postIds")
    List<XhsContent> findByPostIdIn(@Param("postIds") List<String> postIds);
}
