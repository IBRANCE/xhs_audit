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
 * 小红书内容Repository
 */
@Repository
public interface XhsContentRepository extends JpaRepository<XhsContent, Long> {

    Optional<XhsContent> findByPostId(String postId);

    Optional<XhsContent> findByUrl(String url);

    List<XhsContent> findByAuthorIdOrderByPublishedAtDesc(String authorId);

    @Query("SELECT COUNT(c) > 0 FROM XhsContent c WHERE c.postId = :postId AND c.crawledAt > :threshold")
    boolean existsRecentlyCrawled(@Param("postId") String postId, @Param("threshold") LocalDateTime threshold);

    @Query("SELECT c FROM XhsContent c WHERE c.postId IN :postIds")
    List<XhsContent> findByPostIdIn(@Param("postIds") List<String> postIds);
}
