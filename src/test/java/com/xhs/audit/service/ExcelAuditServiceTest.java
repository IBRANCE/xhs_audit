package com.xhs.audit.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;
import com.xhs.audit.service.ExcelAuditService;

/**
 * ExcelAuditService集成测试
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@SpringBootTest
@ActiveProfiles("test")
class ExcelAuditServiceTest {

    @Autowired
    private ExcelAuditService excelAuditService;

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired
    private AuditResultRepository auditResultRepository;

    /**
     * 测试文件大小超限
     */
    @Test
    void testFileSizeExceeded() {
        // 创建超过50MB的文件（模拟）
        byte[] largeContent = new byte[51 * 1024 * 1024]; // 51MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                largeContent);

        // 应该抛出异常
        assertThatThrownBy(() -> excelAuditService.processExcelUpload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小超过50MB限制");
    }

    /**
     * 测试文件格式错误
     */
    @Test
    void testInvalidFileFormat() {
        // 创建错误格式的文件
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes());

        // 应该抛出异常
        assertThatThrownBy(() -> excelAuditService.processExcelUpload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持.xlsx和.xls格式");
    }

    /**
     * 测试空文件
     */
    @Test
    void testEmptyFile() {
        // 创建空文件
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);

        // 应该抛出异常
        assertThatThrownBy(() -> excelAuditService.processExcelUpload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件未上传");
    }

    /**
     * 测试导出结果 - 任务未完成
     */
    @Test
    void testExportResultsJobNotCompleted() {
        // 创建未完成的任务
        AuditJob job = new AuditJob();
        job.setJobId("test-job-processing");
        job.setStatus("PROCESSING");
        job.setTotalLinks(10);
        job.setCompletedCount(5);
        job.setSuccessCount(0);
        job.setFailedCount(0);
        auditJobRepository.save(job);

        // 应该抛出异常
        assertThatThrownBy(() -> excelAuditService.exportResultsToExcel("test-job-processing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务尚未完成");
    }

    /**
     * 测试导出结果 - 任务不存在
     */
    @Test
    void testExportResultsJobNotFound() {
        // 应该抛出异常
        assertThatThrownBy(() -> excelAuditService.exportResultsToExcel("non-existent-job"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
    }
}
