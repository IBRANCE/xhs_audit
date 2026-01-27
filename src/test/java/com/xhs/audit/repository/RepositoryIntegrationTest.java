package com.xhs.audit.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Repository集成测试
 * 使用H2内存数据库测试所有数据访问对象
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Repository - 数据层集成测试")
class RepositoryIntegrationTest {

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired
    private AuditResultRepository auditResultRepository;

    private AuditJob testJob;
    private AuditResult testResult;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        testJob = new AuditJob();
        testJob.setJobId("job-001");
        testJob.setStatus("COMPLETED");
        testJob.setTotalLinks(100);
        testJob.setSuccessCount(80);
        testJob.setFailedCount(20);
        testJob.setCreatedAt(LocalDateTime.now());

        testResult = new AuditResult();
        testResult.setPostId("post-001");
        testResult.setJobId("job-001");
        testResult.setUrl("https://example.com/post/001");
        testResult.setAuditStatus("PASSED");
        testResult.setAuditedAt(LocalDateTime.now());
    }

    /**
     * 测试：保存审核任务到数据库
     */
    @Test
    @DisplayName("AuditJobRepository - 保存任务")
    void testSaveAuditJob() {
        // Act
        AuditJob savedJob = auditJobRepository.save(testJob);

        // Assert
        assertThat(savedJob.getId()).isNotNull();
        assertThat(savedJob.getJobId()).isEqualTo("job-001");
        assertThat(savedJob.getStatus()).isEqualTo("COMPLETED");
        assertThat(savedJob.getTotalLinks()).isEqualTo(100);

        log.info("✓ 保存审核任务测试通过");
    }

    /**
     * 测试：查询审核任务
     */
    @Test
    @DisplayName("AuditJobRepository - 按jobId查询")
    void testFindAuditJobByJobId() {
        // Arrange
        auditJobRepository.save(testJob);

        // Act
        Optional<AuditJob> found = auditJobRepository.findByJobId("job-001");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getJobId()).isEqualTo("job-001");
        assertThat(found.get().getStatus()).isEqualTo("COMPLETED");

        log.info("✓ 查询审核任务测试通过");
    }

    /**
     * 测试：查询不存在的任务
     */
    @Test
    @DisplayName("AuditJobRepository - 查询不存在的任务返回空")
    void testFindNonexistentAuditJob() {
        // Act
        Optional<AuditJob> found = auditJobRepository.findByJobId("nonexistent-job");

        // Assert
        assertThat(found).isEmpty();

        log.info("✓ 查询不存在任务测试通过");
    }

    /**
     * 测试：保存审核结果
     */
    @Test
    @DisplayName("AuditResultRepository - 保存审核结果")
    void testSaveAuditResult() {
        // Act
        AuditResult savedResult = auditResultRepository.save(testResult);

        // Assert
        assertThat(savedResult.getId()).isNotNull();
        assertThat(savedResult.getPostId()).isEqualTo("post-001");
        assertThat(savedResult.getJobId()).isEqualTo("job-001");
        assertThat(savedResult.getAuditStatus()).isEqualTo("PASSED");

        log.info("✓ 保存审核结果测试通过");
    }

    /**
     * 测试：按postId查询审核结果
     */
    @Test
    @DisplayName("AuditResultRepository - 按postId查询")
    void testFindAuditResultByPostId() {
        // Arrange
        auditResultRepository.save(testResult);

        // Act
        Optional<AuditResult> found = auditResultRepository.findByPostId("post-001");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getPostId()).isEqualTo("post-001");
        assertThat(found.get().getAuditStatus()).isEqualTo("PASSED");

        log.info("✓ 按postId查询审核结果测试通过");
    }

    /**
     * 测试：按jobId查询多个审核结果
     */
    @Test
    @DisplayName("AuditResultRepository - 按jobId查询多条结果")
    void testFindAuditResultsByJobId() {
        // Arrange
        AuditResult result1 = new AuditResult();
        result1.setPostId("post-001");
        result1.setJobId("job-001");
        result1.setUrl("https://example.com/post/001");
        result1.setAuditStatus("PASSED");

        AuditResult result2 = new AuditResult();
        result2.setPostId("post-002");
        result2.setJobId("job-001");
        result2.setUrl("https://example.com/post/002");
        result2.setAuditStatus("REJECTED");

        auditResultRepository.saveAll(List.of(result1, result2));

        // Act
        List<AuditResult> results = auditResultRepository.findByJobId("job-001");

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results).extracting("postId").contains("post-001", "post-002");

        log.info("✓ 按jobId查询多条结果测试通过");
    }

    /**
     * 测试：分页查询审核结果
     */
    @Test
    @DisplayName("AuditResultRepository - 分页查询")
    void testFindAuditResultsWithPagination() {
        // Arrange
        for (int i = 1; i <= 15; i++) {
            AuditResult result = new AuditResult();
            result.setPostId("post-" + i);
            result.setJobId("job-001");
            result.setUrl("https://example.com/post/" + i);
            result.setAuditStatus("PASSED");
            auditResultRepository.save(result);
        }

        // Act
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditResult> page = auditResultRepository.findByJobId("job-001", pageable);

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(10);

        log.info("✓ 分页查询测试通过");
    }

    /**
     * 测试：按审核状态查询
     */
    @Test
    @DisplayName("AuditResultRepository - 按审核状态查询")
    void testFindAuditResultsByStatus() {
        // Arrange
        AuditResult passed = new AuditResult();
        passed.setPostId("post-passed");
        passed.setJobId("job-001");
        passed.setUrl("https://example.com/post/passed");
        passed.setAuditStatus("PASSED");

        AuditResult rejected = new AuditResult();
        rejected.setPostId("post-rejected");
        rejected.setJobId("job-001");
        rejected.setUrl("https://example.com/post/rejected");
        rejected.setAuditStatus("REJECTED");

        auditResultRepository.saveAll(List.of(passed, rejected));

        // Act
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditResult> results = auditResultRepository.findByAuditStatus("PASSED", pageable);

        // Assert
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getAuditStatus()).isEqualTo("PASSED");

        log.info("✓ 按审核状态查询测试通过");
    }

    /**
     * 测试：统计特定jobId和状态的结果数
     */
    @Test
    @DisplayName("AuditResultRepository - 统计特定状态的结果数")
    void testCountByJobIdAndStatus() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            AuditResult result = new AuditResult();
            result.setPostId("post-pass-" + i);
            result.setJobId("job-001");
            result.setUrl("https://example.com/post/pass/" + i);
            result.setAuditStatus("PASSED");
            auditResultRepository.save(result);
        }

        for (int i = 0; i < 3; i++) {
            AuditResult result = new AuditResult();
            result.setPostId("post-reject-" + i);
            result.setJobId("job-001");
            result.setUrl("https://example.com/post/reject/" + i);
            result.setAuditStatus("REJECTED");
            auditResultRepository.save(result);
        }

        // Act
        long passedCount = auditResultRepository.countByJobIdAndAuditStatus("job-001", "PASSED");
        long rejectedCount = auditResultRepository.countByJobIdAndAuditStatus("job-001", "REJECTED");

        // Assert
        assertThat(passedCount).isEqualTo(5);
        assertThat(rejectedCount).isEqualTo(3);

        log.info("✓ 统计特定状态结果数测试通过");
    }

    /**
     * 测试：获取状态统计信息
     */
    @Test
    @DisplayName("AuditResultRepository - 获取状态统计")
    void testGetStatusStatsByJobId() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            AuditResult result = new AuditResult();
            result.setPostId("post-" + i);
            result.setJobId("job-001");
            result.setUrl("https://example.com/post/" + i);
            result.setAuditStatus("PASSED");
            auditResultRepository.save(result);
        }

        AuditResult rejected = new AuditResult();
        rejected.setPostId("post-reject");
        rejected.setJobId("job-001");
        rejected.setUrl("https://example.com/post/reject");
        rejected.setAuditStatus("REJECTED");
        auditResultRepository.save(rejected);

        // Act
        List<Object[]> stats = auditResultRepository.getStatusStatsByJobId("job-001");

        // Assert
        assertThat(stats).isNotEmpty();
        assertThat(stats).hasSize(2); // PASSED and REJECTED

        log.info("✓ 获取状态统计测试通过: {}", stats);
    }

    /**
     * 测试：按时间范围查询
     */
    @Test
    @DisplayName("AuditResultRepository - 按时间范围查询")
    void testFindByDateRange() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime tomorrow = now.plusDays(1);

        AuditResult result = new AuditResult();
        result.setPostId("post-001");
        result.setJobId("job-001");
        result.setUrl("https://example.com/post/001");
        result.setAuditStatus("PASSED");
        result.setAuditedAt(now);
        auditResultRepository.save(result);

        // Act
        List<AuditResult> results = auditResultRepository
                .findByAuditedAtBetween(yesterday, tomorrow);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPostId()).isEqualTo("post-001");

        log.info("✓ 按时间范围查询测试通过");
    }
}
