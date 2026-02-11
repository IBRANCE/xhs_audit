package com.xhs.audit.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JobStatistics 单元测试")
class JobStatisticsTest {

    @Test
    @DisplayName("构建 JobStatistics - 使用 builder")
    void testBuilder() {
        // When
        JobStatistics stats = JobStatistics.builder()
                .jobId("job123")
                .completedCount(100)
                .passedCount(80)
                .rejectedCount(15)
                .uncertainCount(5)
                .build();

        // Then
        assertThat(stats.getJobId()).isEqualTo("job123");
        assertThat(stats.getCompletedCount()).isEqualTo(100);
        assertThat(stats.getPassedCount()).isEqualTo(80);
        assertThat(stats.getRejectedCount()).isEqualTo(15);
        assertThat(stats.getUncertainCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("构建 JobStatistics - 使用无参构造函数")
    void testNoArgsConstructor() {
        // When
        JobStatistics stats = new JobStatistics();

        // Then
        assertThat(stats.getJobId()).isNull();
        assertThat(stats.getCompletedCount()).isNull();
        assertThat(stats.getPassedCount()).isNull();
        assertThat(stats.getRejectedCount()).isNull();
        assertThat(stats.getUncertainCount()).isNull();
    }

    @Test
    @DisplayName("构建 JobStatistics - 使用全参构造函数")
    void testAllArgsConstructor() {
        // When
        JobStatistics stats = new JobStatistics(
                "job123", 100, 80, 15, 5);

        // Then
        assertThat(stats.getJobId()).isEqualTo("job123");
        assertThat(stats.getCompletedCount()).isEqualTo(100);
        assertThat(stats.getPassedCount()).isEqualTo(80);
        assertThat(stats.getRejectedCount()).isEqualTo(15);
        assertThat(stats.getUncertainCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("计算实际驳回数 - 包含 REJECTED 和 UNCERTAIN")
    void testGetActualRejectedCount_WithRejectedAndUncertain() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .rejectedCount(15)
                .uncertainCount(5)
                .build();

        // When
        Integer actualRejected = stats.getActualRejectedCount();

        // Then
        assertThat(actualRejected).isEqualTo(20);
    }

    @Test
    @DisplayName("计算实际驳回数 - 只有 REJECTED")
    void testGetActualRejectedCount_OnlyRejected() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .rejectedCount(15)
                .uncertainCount(0)
                .build();

        // When
        Integer actualRejected = stats.getActualRejectedCount();

        // Then
        assertThat(actualRejected).isEqualTo(15);
    }

    @Test
    @DisplayName("计算实际驳回数 - 只有 UNCERTAIN")
    void testGetActualRejectedCount_OnlyUncertain() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .rejectedCount(0)
                .uncertainCount(5)
                .build();

        // When
        Integer actualRejected = stats.getActualRejectedCount();

        // Then
        assertThat(actualRejected).isEqualTo(5);
    }

    @Test
    @DisplayName("计算实际驳回数 - 都为 0")
    void testGetActualRejectedCount_AllZero() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .rejectedCount(0)
                .uncertainCount(0)
                .build();

        // When
        Integer actualRejected = stats.getActualRejectedCount();

        // Then
        assertThat(actualRejected).isEqualTo(0);
    }

    @Test
    @DisplayName("计算实际驳回数 - null 值处理")
    void testGetActualRejectedCount_WithNullValues() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .rejectedCount(null)
                .uncertainCount(null)
                .build();

        // When
        Integer actualRejected = stats.getActualRejectedCount();

        // Then - null 值应该被当作 0 处理
        assertThat(actualRejected).isEqualTo(0);
    }

    @Test
    @DisplayName("计算实际驳回数 - 混合 null 和非 null")
    void testGetActualRejectedCount_MixedNullAndNonNull() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .rejectedCount(10)
                .uncertainCount(null)
                .build();

        // When
        Integer actualRejected = stats.getActualRejectedCount();

        // Then
        assertThat(actualRejected).isEqualTo(10);
    }

    @Test
    @DisplayName("计算进度百分比 - 0%")
    void testCalculateProgressPercent_ZeroPercent() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(0)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(100);

        // Then
        assertThat(progress).isEqualTo(0);
    }

    @Test
    @DisplayName("计算进度百分比 - 50%")
    void testCalculateProgressPercent_FiftyPercent() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(50)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(100);

        // Then
        assertThat(progress).isEqualTo(50);
    }

    @Test
    @DisplayName("计算进度百分比 - 100%")
    void testCalculateProgressPercent_OneHundredPercent() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(100)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(100);

        // Then
        assertThat(progress).isEqualTo(100);
    }

    @Test
    @DisplayName("计算进度百分比 - 四舍五入")
    void testCalculateProgressPercent_Rounding() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(75)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(100);

        // Then
        assertThat(progress).isEqualTo(75);
    }

    @Test
    @DisplayName("计算进度百分比 - 总数为 0")
    void testCalculateProgressPercent_TotalZero() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(50)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(0);

        // Then - 总数为 0 时应该返回 0
        assertThat(progress).isEqualTo(0);
    }

    @Test
    @DisplayName("计算进度百分比 - 总数为 null")
    void testCalculateProgressPercent_TotalNull() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(50)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(null);

        // Then - 总数为 null 时应该返回 0
        assertThat(progress).isEqualTo(0);
    }

    @Test
    @DisplayName("计算进度百分比 - 完成数为 null")
    void testCalculateProgressPercent_CompletedNull() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(null)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(100);

        // Then - 完成数为 null 时应该返回 0
        assertThat(progress).isEqualTo(0);
    }

    @Test
    @DisplayName("计算进度百分比 - 都为 null")
    void testCalculateProgressPercent_AllNull() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(null)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(null);

        // Then
        assertThat(progress).isEqualTo(0);
    }

    @Test
    @DisplayName("计算进度百分比 - 超过 100%")
    void testCalculateProgressPercent_OverOneHundredPercent() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(150)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(100);

        // Then - 允许超过 100%
        assertThat(progress).isEqualTo(150);
    }

    @Test
    @DisplayName("计算进度百分比 - 小数应该被截断")
    void testCalculateProgressPercent_DecimalTruncated() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(33)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(100);

        // Then - 33/100 = 33%，不是 33.33%
        assertThat(progress).isEqualTo(33);
    }

    @Test
    @DisplayName("计算进度百分比 - 大数测试")
    void testCalculateProgressPercent_LargeNumbers() {
        // Given
        JobStatistics stats = JobStatistics.builder()
                .completedCount(9999)
                .build();

        // When
        Integer progress = stats.calculateProgressPercent(10000);

        // Then
        assertThat(progress).isEqualTo(99);
    }

    @Test
    @DisplayName("测试 setter 方法")
    void testSetters() {
        // Given
        JobStatistics stats = new JobStatistics();

        // When
        stats.setJobId("job456");
        stats.setCompletedCount(200);
        stats.setPassedCount(150);
        stats.setRejectedCount(30);
        stats.setUncertainCount(20);

        // Then
        assertThat(stats.getJobId()).isEqualTo("job456");
        assertThat(stats.getCompletedCount()).isEqualTo(200);
        assertThat(stats.getPassedCount()).isEqualTo(150);
        assertThat(stats.getRejectedCount()).isEqualTo(30);
        assertThat(stats.getUncertainCount()).isEqualTo(20);
    }
}