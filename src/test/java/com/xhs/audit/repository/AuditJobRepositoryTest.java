package com.xhs.audit.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xhs.audit.model.entity.AuditJob;

/**
 * AuditJobRepository Unit Tests
 * Uses Mockito to test repository operations
 *
 * @author XHS Audit System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditJobRepository - Unit Tests")
class AuditJobRepositoryTest {

    @Mock
    private AuditJobRepository auditJobRepository;

    private AuditJob testJob;

    @BeforeEach
    void setUp() {
        testJob = AuditJob.builder()
                .id(1L)
                .jobId("test-job-001")
                .url("https://example.com/post/001")
                .totalLinks(10)
                .completedCount(5)
                .successCount(4)
                .failedCount(1)
                .status("PROCESSING")
                .message("Processing in progress")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @DisplayName("Should save AuditJob successfully")
        void testSaveAuditJob() {
            // Arrange
            when(auditJobRepository.save(any(AuditJob.class))).thenReturn(testJob);

            // Act
            AuditJob savedJob = auditJobRepository.save(testJob);

            // Assert
            assertThat(savedJob).isNotNull();
            assertThat(savedJob.getJobId()).isEqualTo("test-job-001");
            assertThat(savedJob.getTotalLinks()).isEqualTo(10);
            assertThat(savedJob.getStatus()).isEqualTo("PROCESSING");

            verify(auditJobRepository, times(1)).save(any(AuditJob.class));
        }

        @Test
        @DisplayName("Should save AuditJob with all fields")
        void testSaveAuditJobWithAllFields() {
            // Arrange
            AuditJob job = AuditJob.builder()
                    .jobId("full-job-002")
                    .url("https://example.com/post/002")
                    .totalLinks(100)
                    .completedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .status("PENDING")
                    .fileName("test_file.xlsx")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(auditJobRepository.save(any(AuditJob.class))).thenReturn(job);

            // Act
            AuditJob savedJob = auditJobRepository.save(job);

            // Assert
            assertThat(savedJob.getJobId()).isEqualTo("full-job-002");
            assertThat(savedJob.getFileName()).isEqualTo("test_file.xlsx");
            assertThat(savedJob.getTotalLinks()).isEqualTo(100);

            verify(auditJobRepository, times(1)).save(any(AuditJob.class));
        }

        @Test
        @DisplayName("Should save multiple AuditJobs")
        void testSaveMultipleAuditJobs() {
            // Arrange
            AuditJob job1 = AuditJob.builder().jobId("job-001").totalLinks(10).status("PENDING").build();
            AuditJob job2 = AuditJob.builder().jobId("job-002").totalLinks(20).status("PENDING").build();

            when(auditJobRepository.saveAll(anyList())).thenReturn(List.of(job1, job2));

            // Act
            List<AuditJob> savedJobs = auditJobRepository.saveAll(List.of(job1, job2));

            // Assert
            assertThat(savedJobs).hasSize(2);
            assertThat(savedJobs).extracting("jobId").containsExactly("job-001", "job-002");

            verify(auditJobRepository, times(1)).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("Update Operations")
    class UpdateOperations {

        @Test
        @DisplayName("Should update AuditJob status")
        void testUpdateAuditJobStatus() {
            // Arrange
            AuditJob updatedJob = AuditJob.builder()
                    .id(1L)
                    .jobId("test-job-001")
                    .status("COMPLETED")
                    .completedCount(10)
                    .successCount(8)
                    .failedCount(2)
                    .completedAt(LocalDateTime.now())
                    .build();

            when(auditJobRepository.save(any(AuditJob.class))).thenReturn(updatedJob);

            // Act
            AuditJob result = auditJobRepository.save(updatedJob);

            // Assert
            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            assertThat(result.getCompletedCount()).isEqualTo(10);

            verify(auditJobRepository, times(1)).save(any(AuditJob.class));
        }

        @Test
        @DisplayName("Should update AuditJob counters")
        void testUpdateAuditJobCounters() {
            // Arrange
            testJob.setSuccessCount(5);
            testJob.setFailedCount(1);
            testJob.setCompletedCount(6);

            when(auditJobRepository.save(any(AuditJob.class))).thenReturn(testJob);

            // Act
            AuditJob result = auditJobRepository.save(testJob);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(5);
            assertThat(result.getFailedCount()).isEqualTo(1);
            assertThat(result.getCompletedCount()).isEqualTo(6);

            verify(auditJobRepository, times(1)).save(any(AuditJob.class));
        }
    }

    @Nested
    @DisplayName("Find By JobId")
    class FindByJobId {

        @Test
        @DisplayName("Should find AuditJob by jobId successfully")
        void testFindByJobId_Success() {
            // Arrange
            when(auditJobRepository.findByJobId("test-job-001")).thenReturn(Optional.of(testJob));

            // Act
            Optional<AuditJob> found = auditJobRepository.findByJobId("test-job-001");

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getJobId()).isEqualTo("test-job-001");
            assertThat(found.get().getStatus()).isEqualTo("PROCESSING");

            verify(auditJobRepository, times(1)).findByJobId("test-job-001");
        }

        @Test
        @DisplayName("Should return empty when jobId not found")
        void testFindByJobId_Empty() {
            // Arrange
            when(auditJobRepository.findByJobId("nonexistent-job")).thenReturn(Optional.empty());

            // Act
            Optional<AuditJob> found = auditJobRepository.findByJobId("nonexistent-job");

            // Assert
            assertThat(found).isEmpty();

            verify(auditJobRepository, times(1)).findByJobId("nonexistent-job");
        }

        @Test
        @DisplayName("Should find AuditJob by id")
        void testFindById() {
            // Arrange
            when(auditJobRepository.findById(1L)).thenReturn(Optional.of(testJob));

            // Act
            Optional<AuditJob> found = auditJobRepository.findById(1L);

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(1L);
            assertThat(found.get().getJobId()).isEqualTo("test-job-001");
        }

        @Test
        @DisplayName("Should return empty when id not found")
        void testFindById_Empty() {
            // Arrange
            when(auditJobRepository.findById(999L)).thenReturn(Optional.empty());

            // Act
            Optional<AuditJob> found = auditJobRepository.findById(999L);

            // Assert
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Find All Operations")
    class FindAllOperations {

        @Test
        @DisplayName("Should find all AuditJobs")
        void testFindAll() {
            // Arrange
            AuditJob job1 = AuditJob.builder().jobId("job-001").build();
            AuditJob job2 = AuditJob.builder().jobId("job-002").build();

            when(auditJobRepository.findAll()).thenReturn(List.of(job1, job2));

            // Act
            List<AuditJob> results = auditJobRepository.findAll();

            // Assert
            assertThat(results).hasSize(2);
            assertThat(results).extracting("jobId").containsExactly("job-001", "job-002");
        }

        @Test
        @DisplayName("Should return empty list when no jobs exist")
        void testFindAll_Empty() {
            // Arrange
            when(auditJobRepository.findAll()).thenReturn(List.of());

            // Act
            List<AuditJob> results = auditJobRepository.findAll();

            // Assert
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete AuditJob by id")
        void testDeleteAuditJob() {
            // Arrange
            doNothing().when(auditJobRepository).deleteById(1L);

            // Act
            auditJobRepository.deleteById(1L);

            // Assert
            verify(auditJobRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("Should delete AuditJob entity")
        void testDeleteAuditJob_Entity() {
            // Arrange
            doNothing().when(auditJobRepository).delete(any(AuditJob.class));

            // Act
            auditJobRepository.delete(testJob);

            // Assert
            verify(auditJobRepository, times(1)).delete(any(AuditJob.class));
        }

        @Test
        @DisplayName("Should delete all AuditJobs")
        void testDeleteAllAuditJobs() {
            // Arrange
            doNothing().when(auditJobRepository).deleteAll();

            // Act
            auditJobRepository.deleteAll();

            // Assert
            verify(auditJobRepository, times(1)).deleteAll();
        }

        @Test
        @DisplayName("Should check if AuditJob exists by id")
        void testExistsById() {
            // Arrange
            when(auditJobRepository.existsById(1L)).thenReturn(true);
            when(auditJobRepository.existsById(999L)).thenReturn(false);

            // Act & Assert
            assertThat(auditJobRepository.existsById(1L)).isTrue();
            assertThat(auditJobRepository.existsById(999L)).isFalse();

            verify(auditJobRepository, times(1)).existsById(1L);
            verify(auditJobRepository, times(1)).existsById(999L);
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperations {

        @Test
        @DisplayName("Should count all AuditJobs")
        void testCount() {
            // Arrange
            when(auditJobRepository.count()).thenReturn(50L);

            // Act
            long count = auditJobRepository.count();

            // Assert
            assertThat(count).isEqualTo(50L);

            verify(auditJobRepository, times(1)).count();
        }

        @Test
        @DisplayName("Should return zero count when no jobs")
        void testCount_Zero() {
            // Arrange
            when(auditJobRepository.count()).thenReturn(0L);

            // Act
            long count = auditJobRepository.count();

            // Assert
            assertThat(count).isEqualTo(0L);

            verify(auditJobRepository, times(1)).count();
        }
    }

    @Nested
    @DisplayName("Entity Helper Methods")
    class EntityHelperMethods {

        @Test
        @DisplayName("Should increment completed count")
        void testIncrementCompletedCount() {
            // Arrange
            testJob.setCompletedCount(5);
            when(auditJobRepository.save(any(AuditJob.class))).thenReturn(testJob);

            // Act
            testJob.incrementCompletedCount();
            auditJobRepository.save(testJob);

            // Assert
            assertThat(testJob.getCompletedCount()).isEqualTo(6);

            verify(auditJobRepository, times(1)).save(any(AuditJob.class));
        }

        @Test
        @DisplayName("Should increment success count")
        void testIncrementSuccessCount() {
            // Arrange
            testJob.setSuccessCount(4);
            when(auditJobRepository.save(any(AuditJob.class))).thenReturn(testJob);

            // Act
            testJob.incrementSuccessCount();
            auditJobRepository.save(testJob);

            // Assert
            assertThat(testJob.getSuccessCount()).isEqualTo(5);

            verify(auditJobRepository, times(1)).save(any(AuditJob.class));
        }

        @Test
        @DisplayName("Should increment failed count")
        void testIncrementFailedCount() {
            // Arrange
            testJob.setFailedCount(1);
            when(auditJobRepository.save(any(AuditJob.class))).thenReturn(testJob);

            // Act
            testJob.incrementFailedCount();
            auditJobRepository.save(testJob);

            // Assert
            assertThat(testJob.getFailedCount()).isEqualTo(2);

            verify(auditJobRepository, times(1)).save(any(AuditJob.class));
        }

        @Test
        @DisplayName("Should check if job is completed")
        void testIsCompleted() {
            // Arrange
            testJob.setTotalLinks(10);
            testJob.setCompletedCount(10);

            // Act & Assert
            assertThat(testJob.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("Should return false when job is not completed")
        void testIsCompleted_NotFinished() {
            // Arrange
            testJob.setTotalLinks(10);
            testJob.setCompletedCount(5);

            // Act & Assert
            assertThat(testJob.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("Should calculate progress percent")
        void testGetProgressPercent() {
            // Arrange
            testJob.setTotalLinks(100);
            testJob.setCompletedCount(50);

            // Act & Assert
            assertThat(testJob.getProgressPercent()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should return zero progress when total links is zero")
        void testGetProgressPercent_ZeroLinks() {
            // Arrange
            testJob.setTotalLinks(0);
            testJob.setCompletedCount(0);

            // Act & Assert
            assertThat(testJob.getProgressPercent()).isEqualTo(0);
        }
    }
}
