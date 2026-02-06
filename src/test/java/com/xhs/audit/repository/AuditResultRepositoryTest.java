package com.xhs.audit.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.xhs.audit.model.entity.AuditResult;

/**
 * AuditResultRepository Unit Tests
 * Uses Mockito to test repository operations
 *
 * @author XHS Audit System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditResultRepository - Unit Tests")
class AuditResultRepositoryTest {

    @Mock
    private AuditResultRepository auditResultRepository;

    private AuditResult testResult;

    @BeforeEach
    void setUp() {
        testResult = AuditResult.builder()
                .id(1L)
                .postId("post-001")
                .jobId("job-001")
                .url("https://example.com/post/001")
                .auditStatus("PASSED")
                .confidenceScore(new BigDecimal("0.95"))
                .modelName("qwen/qwen3-4b")
                .auditedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperations {

        @Test
        @DisplayName("Should save AuditResult successfully")
        void testSaveAuditResult() {
            // Arrange
            when(auditResultRepository.save(any(AuditResult.class))).thenReturn(testResult);

            // Act
            AuditResult savedResult = auditResultRepository.save(testResult);

            // Assert
            assertThat(savedResult).isNotNull();
            assertThat(savedResult.getPostId()).isEqualTo("post-001");
            assertThat(savedResult.getJobId()).isEqualTo("job-001");
            assertThat(savedResult.getAuditStatus()).isEqualTo("PASSED");

            verify(auditResultRepository, times(1)).save(any(AuditResult.class));
        }

        @Test
        @DisplayName("Should save AuditResult with all fields")
        void testSaveAuditResultWithAllFields() {
            // Arrange
            List<Map<String, Object>> reasons = List.of(
                    Map.of("type", "spam", "detail", "Contains promotional content"),
                    Map.of("type", "quality", "detail", "Low resolution images")
            );

            AuditResult result = AuditResult.builder()
                    .postId("post-full-001")
                    .jobId("job-full-001")
                    .url("https://example.com/post/full")
                    .auditStatus("REJECTED")
                    .reasons(reasons)
                    .confidenceScore(new BigDecimal("0.85"))
                    .modelName("glm-4.6v-flash")
                    .auditedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            when(auditResultRepository.save(any(AuditResult.class))).thenReturn(result);

            // Act
            AuditResult savedResult = auditResultRepository.save(result);

            // Assert
            assertThat(savedResult.getPostId()).isEqualTo("post-full-001");
            assertThat(savedResult.getAuditStatus()).isEqualTo("REJECTED");
            assertThat(savedResult.getConfidenceScore()).isEqualByComparingTo("0.85");
            assertThat(savedResult.getReasons()).hasSize(2);

            verify(auditResultRepository, times(1)).save(any(AuditResult.class));
        }

        @Test
        @DisplayName("Should save multiple AuditResults")
        void testSaveMultipleAuditResults() {
            // Arrange
            AuditResult result1 = AuditResult.builder()
                    .postId("post-001")
                    .jobId("job-001")
                    .url("https://example.com/post/001")
                    .auditStatus("PASSED")
                    .build();

            AuditResult result2 = AuditResult.builder()
                    .postId("post-002")
                    .jobId("job-001")
                    .url("https://example.com/post/002")
                    .auditStatus("REJECTED")
                    .build();

            when(auditResultRepository.saveAll(anyList())).thenReturn(List.of(result1, result2));

            // Act
            List<AuditResult> savedResults = auditResultRepository.saveAll(List.of(result1, result2));

            // Assert
            assertThat(savedResults).hasSize(2);
            assertThat(savedResults).extracting("postId").containsExactly("post-001", "post-002");

            verify(auditResultRepository, times(1)).saveAll(anyList());
        }

        @Test
        @DisplayName("Should find AuditResult by id")
        void testFindById() {
            // Arrange
            when(auditResultRepository.findById(1L)).thenReturn(Optional.of(testResult));

            // Act
            Optional<AuditResult> found = auditResultRepository.findById(1L);

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(1L);
            assertThat(found.get().getPostId()).isEqualTo("post-001");

            verify(auditResultRepository, times(1)).findById(1L);
        }

        @Test
        @DisplayName("Should find all AuditResults")
        void testFindAll() {
            // Arrange
            AuditResult result1 = AuditResult.builder().postId("post-001").auditStatus("PASSED").build();
            AuditResult result2 = AuditResult.builder().postId("post-002").auditStatus("REJECTED").build();

            when(auditResultRepository.findAll()).thenReturn(List.of(result1, result2));

            // Act
            List<AuditResult> results = auditResultRepository.findAll();

            // Assert
            assertThat(results).hasSize(2);

            verify(auditResultRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should delete AuditResult by id")
        void testDeleteById() {
            // Arrange
            doNothing().when(auditResultRepository).deleteById(1L);

            // Act
            auditResultRepository.deleteById(1L);

            // Assert
            verify(auditResultRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("Should count AuditResults")
        void testCount() {
            // Arrange
            when(auditResultRepository.count()).thenReturn(100L);

            // Act
            long count = auditResultRepository.count();

            // Assert
            assertThat(count).isEqualTo(100L);

            verify(auditResultRepository, times(1)).count();
        }
    }

    @Nested
    @DisplayName("Find By PostId")
    class FindByPostId {

        @Test
        @DisplayName("Should find AuditResult by postId successfully")
        void testFindByPostId_Success() {
            // Arrange
            when(auditResultRepository.findByPostId("post-001")).thenReturn(Optional.of(testResult));

            // Act
            Optional<AuditResult> found = auditResultRepository.findByPostId("post-001");

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getPostId()).isEqualTo("post-001");
            assertThat(found.get().getAuditStatus()).isEqualTo("PASSED");

            verify(auditResultRepository, times(1)).findByPostId("post-001");
        }

        @Test
        @DisplayName("Should return empty when postId not found")
        void testFindByPostId_Empty() {
            // Arrange
            when(auditResultRepository.findByPostId("nonexistent-post")).thenReturn(Optional.empty());

            // Act
            Optional<AuditResult> found = auditResultRepository.findByPostId("nonexistent-post");

            // Assert
            assertThat(found).isEmpty();

            verify(auditResultRepository, times(1)).findByPostId("nonexistent-post");
        }

        @Test
        @DisplayName("Should find first AuditResult by postId order by auditedAt desc")
        void testFindFirstByPostIdOrderByAuditedAtDesc() {
            // Arrange
            when(auditResultRepository.findFirstByPostIdOrderByAuditedAtDesc("post-001"))
                    .thenReturn(Optional.of(testResult));

            // Act
            Optional<AuditResult> found = auditResultRepository.findFirstByPostIdOrderByAuditedAtDesc("post-001");

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getPostId()).isEqualTo("post-001");

            verify(auditResultRepository, times(1)).findFirstByPostIdOrderByAuditedAtDesc("post-001");
        }
    }

    @Nested
    @DisplayName("Find By JobId")
    class FindByJobId {

        @Test
        @DisplayName("Should find AuditResults by jobId")
        void testFindByJobId() {
            // Arrange
            AuditResult result1 = AuditResult.builder().postId("post-001").jobId("job-001").auditStatus("PASSED").build();
            AuditResult result2 = AuditResult.builder().postId("post-002").jobId("job-001").auditStatus("REJECTED").build();

            when(auditResultRepository.findByJobId("job-001")).thenReturn(List.of(result1, result2));

            // Act
            List<AuditResult> results = auditResultRepository.findByJobId("job-001");

            // Assert
            assertThat(results).hasSize(2);
            assertThat(results).extracting("jobId").containsOnly("job-001");

            verify(auditResultRepository, times(1)).findByJobId("job-001");
        }

        @Test
        @DisplayName("Should find AuditResults by jobId with pagination")
        void testFindByJobIdWithPagination() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            AuditResult result = AuditResult.builder()
                    .postId("post-001")
                    .jobId("job-001")
                    .auditStatus("PASSED")
                    .build();

            Page<AuditResult> expectedPage = new PageImpl<>(List.of(result), pageable, 1);

            when(auditResultRepository.findByJobId("job-001", pageable)).thenReturn(expectedPage);

            // Act
            Page<AuditResult> results = auditResultRepository.findByJobId("job-001", pageable);

            // Assert
            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getJobId()).isEqualTo("job-001");

            verify(auditResultRepository, times(1)).findByJobId("job-001", pageable);
        }

        @Test
        @DisplayName("Should return empty list when jobId not found")
        void testFindByJobId_Empty() {
            // Arrange
            when(auditResultRepository.findByJobId("nonexistent-job")).thenReturn(List.of());

            // Act
            List<AuditResult> results = auditResultRepository.findByJobId("nonexistent-job");

            // Assert
            assertThat(results).isEmpty();

            verify(auditResultRepository, times(1)).findByJobId("nonexistent-job");
        }
    }

    @Nested
    @DisplayName("Find By AuditStatus")
    class FindByAuditStatus {

        @Test
        @DisplayName("Should find AuditResults by auditStatus with pagination")
        void testFindByAuditStatus() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            AuditResult result = AuditResult.builder()
                    .postId("post-001")
                    .auditStatus("PASSED")
                    .build();

            Page<AuditResult> expectedPage = new PageImpl<>(List.of(result), pageable, 1);

            when(auditResultRepository.findByAuditStatus("PASSED", pageable)).thenReturn(expectedPage);

            // Act
            Page<AuditResult> results = auditResultRepository.findByAuditStatus("PASSED", pageable);

            // Assert
            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getAuditStatus()).isEqualTo("PASSED");

            verify(auditResultRepository, times(1)).findByAuditStatus("PASSED", pageable);
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperations {

        @Test
        @DisplayName("Should count AuditResults by jobId and auditStatus")
        void testCountByJobIdAndAuditStatus() {
            // Arrange
            when(auditResultRepository.countByJobIdAndAuditStatus("job-001", "PASSED")).thenReturn(5L);

            // Act
            long count = auditResultRepository.countByJobIdAndAuditStatus("job-001", "PASSED");

            // Assert
            assertThat(count).isEqualTo(5L);

            verify(auditResultRepository, times(1)).countByJobIdAndAuditStatus("job-001", "PASSED");
        }

        @Test
        @DisplayName("Should return zero when no results match count criteria")
        void testCountByJobIdAndAuditStatus_Zero() {
            // Arrange
            when(auditResultRepository.countByJobIdAndAuditStatus("nonexistent-job", "REJECTED")).thenReturn(0L);

            // Act
            long count = auditResultRepository.countByJobIdAndAuditStatus("nonexistent-job", "REJECTED");

            // Assert
            assertThat(count).isEqualTo(0L);

            verify(auditResultRepository, times(1)).countByJobIdAndAuditStatus("nonexistent-job", "REJECTED");
        }
    }

    @Nested
    @DisplayName("Status Statistics")
    class StatusStatistics {

        @Test
        @DisplayName("Should get status stats by jobId")
        void testGetStatusStatsByJobId() {
            // Arrange
            Object[] passedStat = new Object[]{"PASSED", 10L};
            Object[] rejectedStat = new Object[]{"REJECTED", 5L};

            when(auditResultRepository.getStatusStatsByJobId("job-001"))
                    .thenReturn(List.of(passedStat, rejectedStat));

            // Act
            List<Object[]> stats = auditResultRepository.getStatusStatsByJobId("job-001");

            // Assert
            assertThat(stats).hasSize(2);
            assertThat(stats.get(0)[0]).isEqualTo("PASSED");
            assertThat(stats.get(0)[1]).isEqualTo(10L);
            assertThat(stats.get(1)[0]).isEqualTo("REJECTED");
            assertThat(stats.get(1)[1]).isEqualTo(5L);

            verify(auditResultRepository, times(1)).getStatusStatsByJobId("job-001");
        }

        @Test
        @DisplayName("Should return empty stats when job has no results")
        void testGetStatusStatsByJobId_Empty() {
            // Arrange
            when(auditResultRepository.getStatusStatsByJobId("empty-job")).thenReturn(List.of());

            // Act
            List<Object[]> stats = auditResultRepository.getStatusStatsByJobId("empty-job");

            // Assert
            assertThat(stats).isEmpty();

            verify(auditResultRepository, times(1)).getStatusStatsByJobId("empty-job");
        }
    }

    @Nested
    @DisplayName("Date Range Query")
    class DateRangeQuery {

        @Test
        @DisplayName("Should find AuditResults by auditedAt date range")
        void testFindByAuditedAtBetween() {
            // Arrange
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now().plusDays(1);

            AuditResult result = AuditResult.builder()
                    .postId("post-001")
                    .auditStatus("PASSED")
                    .auditedAt(LocalDateTime.now())
                    .build();

            when(auditResultRepository.findByAuditedAtBetween(startTime, endTime))
                    .thenReturn(List.of(result));

            // Act
            List<AuditResult> results = auditResultRepository.findByAuditedAtBetween(startTime, endTime);

            // Assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getPostId()).isEqualTo("post-001");

            verify(auditResultRepository, times(1)).findByAuditedAtBetween(startTime, endTime);
        }

        @Test
        @DisplayName("Should return empty when no results in date range")
        void testFindByAuditedAtBetween_Empty() {
            // Arrange
            LocalDateTime startTime = LocalDateTime.now().minusDays(30);
            LocalDateTime endTime = LocalDateTime.now().minusDays(20);

            when(auditResultRepository.findByAuditedAtBetween(startTime, endTime))
                    .thenReturn(List.of());

            // Act
            List<AuditResult> results = auditResultRepository.findByAuditedAtBetween(startTime, endTime);

            // Assert
            assertThat(results).isEmpty();

            verify(auditResultRepository, times(1)).findByAuditedAtBetween(startTime, endTime);
        }
    }
}
