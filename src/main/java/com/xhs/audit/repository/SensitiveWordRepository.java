package com.xhs.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.xhs.audit.model.entity.SensitiveWord;

/**
 * 敏感词Repository
 */
@Repository
public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {

    List<SensitiveWord> findByEnabledTrue();

    List<SensitiveWord> findByCategory(String category);
}
