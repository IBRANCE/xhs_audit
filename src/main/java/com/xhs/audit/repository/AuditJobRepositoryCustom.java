package com.xhs.audit.repository;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.xhs.audit.model.entity.AuditJob;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * AuditJob自定义Repository
 * 使用Specification处理动态查询
 */
@Repository
public class AuditJobRepositoryCustom {

    @Autowired
    private AuditJobRepository auditJobRepository;

    /**
     * 组合条件查询任务列表
     */
    public Page<AuditJob> searchJobs(String jobId, String status, String keyword,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {

        Specification<AuditJob> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 任务ID精确匹配
            if (jobId != null && !jobId.isEmpty()) {
                predicates.add(cb.equal(root.get("jobId"), jobId));
            }

            // 状态筛选
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 文件名模糊搜索
            if (keyword != null && !keyword.isEmpty()) {
                predicates.add(cb.like(root.get("fileName"), "%" + keyword + "%"));
            }

            // 开始时间
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            // 结束时间
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditJobRepository.findAll(spec, pageable);
    }
}
