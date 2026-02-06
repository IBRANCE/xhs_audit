package com.xhs.audit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.model.dto.CrawlTaskMessage;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;

/**
 * ExcelAuditService 单元测试
 *
 * 测试 Excel 审核服务的核心功能：
 * 1. 文件上传验证
 * 2. Excel 链接提取
 * 3. 审核任务创建
 * 4. 结果导出
 *
 * @author XHS Audit System
 */
@ExtendWith(MockitoExtension.class)
class ExcelAuditServiceTest {

    @Mock
    private AuditJobRepository auditJobRepository;

    @Mock
    private AuditResultRepository auditResultRepository;

    @Mock
    private AsyncAuditService asyncAuditService;

    @Mock
    private MessageQueueService messageQueueService;

    @InjectMocks
    private ExcelAuditService excelAuditService;

    private static final String VALID_XLSX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String VALID_XLS_CONTENT_TYPE =
        "application/vnd.ms-excel";

    @Nested
    @DisplayName("文件上传验证测试")
    class FileValidationTests {

        @Test
        @DisplayName("空文件应该抛出异常")
        void testEmptyFile() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.xlsx",
                VALID_XLSX_CONTENT_TYPE,
                new byte[0]);

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                excelAuditService.processExcelUpload(emptyFile);
            });

            assertEquals("ERR_FILE_NOT_FOUND", exception.getCode());
            assertTrue(exception.getMessage().contains("文件未上传"));
        }

        @Test
        @DisplayName("超过50MB的文件应该抛出异常")
        void testFileSizeExceeded() {
            // 创建 51MB 的文件
            byte[] largeContent = new byte[51 * 1024 * 1024];
            MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.xlsx",
                VALID_XLSX_CONTENT_TYPE,
                largeContent);

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                excelAuditService.processExcelUpload(largeFile);
            });

            assertEquals("ERR_FILE_SIZE_EXCEED", exception.getCode());
            assertTrue(exception.getMessage().contains("文件大小超过50MB限制"));
        }

        @Test
        @DisplayName("不支持的文件格式应该抛出异常")
        void testInvalidFileFormat() {
            MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes());

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                excelAuditService.processExcelUpload(txtFile);
            });

            assertEquals("ERR_FILE_FORMAT", exception.getCode());
            assertTrue(exception.getMessage().contains("仅支持.xlsx和.xls格式"));
        }

        @Test
        @DisplayName("错误的 content-type 应该抛出异常")
        void testWrongContentType() {
            MockMultipartFile wrongTypeFile = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/json",
                "{\"data\": \"test\"}".getBytes());

            // 当 content-type 不正确时，POI 解析会失败
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                excelAuditService.processExcelUpload(wrongTypeFile);
            });

            // 可能是 ERR_FILE_FORMAT 或 ERR_FILE_PARSE_FAILED
            assertTrue(exception.getCode().startsWith("ERR_FILE"));
        }

        @Test
        @DisplayName("文件名 null 应该抛出异常")
        void testNullFilename() {
            MockMultipartFile nullNameFile = new MockMultipartFile(
                "file",
                null,
                VALID_XLSX_CONTENT_TYPE,
                "test".getBytes());

            assertThrows(BusinessException.class, () -> {
                excelAuditService.processExcelUpload(nullNameFile);
            });
        }
    }

    @Nested
    @DisplayName("Excel 解析测试")
    class ExcelParsingTests {

        @Test
        @DisplayName("不包含链接的 Excel 应该抛出异常")
        void testNoLinksInExcel() throws Exception {
            MockMultipartFile file = createExcelWithNoLinks();

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                excelAuditService.processExcelUpload(file);
            });

            assertEquals("ERR_NO_VALID_LINKS", exception.getCode());
            assertTrue(exception.getMessage().contains("未找到有效的小红书链接"));
        }

        @Test
        @DisplayName("包含有效链接的 Excel 应该成功解析")
        void testValidLinksInExcel() throws Exception {
            List<String> expectedUrls = Arrays.asList(
                "https://www.xiaohongshu.com/explore/abc123def456",
                "https://www.xiaohongshu.com/explore/def789ghi012"
            );
            MockMultipartFile file = createExcelWithUrls(expectedUrls);

            when(auditJobRepository.save(any(AuditJob.class))).thenAnswer(invocation -> {
                AuditJob job = invocation.getArgument(0);
                job.setId(1L);
                return job;
            });

            String jobId = excelAuditService.processExcelUpload(file);

            assertNotNull(jobId);
            assertTrue(jobId.startsWith("job-"));
            verify(asyncAuditService).processAuditJob(eq(jobId), anyList());
        }

        @Test
        @DisplayName("Excel 中的链接应该去重")
        void testUrlDeduplication() throws Exception {
            List<String> duplicateUrls = Arrays.asList(
                "https://www.xiaohongshu.com/explore/abc123",
                "https://www.xiaohongshu.com/explore/abc123", // 重复
                "https://www.xiaohongshu.com/explore/def456"
            );
            MockMultipartFile file = createExcelWithUrls(duplicateUrls);

            when(auditJobRepository.save(any(AuditJob.class))).thenAnswer(invocation -> {
                AuditJob job = invocation.getArgument(0);
                job.setId(1L);
                return job;
            });

            excelAuditService.processExcelUpload(file);

            verify(asyncAuditService).processAuditJob(anyString(), argThat(urls ->
                urls.size() == 2 // 应该去重后只有2个链接
            ));
        }

        @Test
        @DisplayName("短链接应该正确提取")
        void testShortlinksInExcel() throws Exception {
            List<String> shortlinks = Arrays.asList(
                "https://xhslink.com/o/abc123",
                "https://xhslink.com/o/def456xyz"
            );
            MockMultipartFile file = createExcelWithUrls(shortlinks);

            when(auditJobRepository.save(any(AuditJob.class))).thenAnswer(invocation -> {
                AuditJob job = invocation.getArgument(0);
                job.setId(1L);
                return job;
            });

            String jobId = excelAuditService.processExcelUpload(file);

            assertNotNull(jobId);
            verify(asyncAuditService).processAuditJob(eq(jobId), argThat(urls ->
                urls.size() == 2
            ));
        }
    }

    @Nested
    @DisplayName("任务创建测试")
    class JobCreationTests {

        @Test
        @DisplayName("processExcelUpload 应该创建 PENDING 状态的审核任务")
        void testJobCreation() throws Exception {
            MockMultipartFile file = createExcelWithUrls(List.of(
                "https://www.xiaohongshu.com/explore/abc123"
            ));

            when(auditJobRepository.save(any(AuditJob.class))).thenAnswer(invocation -> {
                AuditJob job = invocation.getArgument(0);
                assertEquals("PENDING", job.getStatus());
                assertEquals(1, job.getTotalLinks());
                assertEquals(0, job.getCompletedCount());
                assertEquals(0, job.getSuccessCount());
                assertEquals(0, job.getFailedCount());
                job.setId(1L);
                return job;
            });

            String jobId = excelAuditService.processExcelUpload(file);

            assertNotNull(jobId);
            assertTrue(jobId.startsWith("job-"));
        }

        @Test
        @DisplayName("任务应该记录文件名")
        void testJobRecordsFilename() throws Exception {
            String filename = "test_links.xlsx";
            MockMultipartFile file = createExcelWithUrls(
                List.of("https://www.xiaohongshu.com/explore/abc123"),
                filename
            );

            when(auditJobRepository.save(any(AuditJob.class))).thenAnswer(invocation -> {
                AuditJob job = invocation.getArgument(0);
                assertEquals(filename, job.getFileName());
                job.setId(1L);
                return job;
            });

            excelAuditService.processExcelUpload(file);

            verify(auditJobRepository).save(any(AuditJob.class));
        }
    }

    @Nested
    @DisplayName("异步模式测试")
    class AsyncModeTests {

        @Test
        @DisplayName("processExcelUploadAsync 应该发送任务到 Redis Stream")
        void testAsyncUploadSendsToRedis() throws Exception {
            List<String> urls = Arrays.asList(
                "https://www.xiaohongshu.com/explore/abc123",
                "https://www.xiaohongshu.com/explore/def456"
            );
            MockMultipartFile file = createExcelWithUrls(urls);

            when(auditJobRepository.save(any(AuditJob.class))).thenAnswer(invocation -> {
                AuditJob job = invocation.getArgument(0);
                job.setId(1L);
                return job;
            });

            when(messageQueueService.sendCrawlTask(any(CrawlTaskMessage.class)))
                .thenReturn("record-123");

            String jobId = excelAuditService.processExcelUploadAsync(file);

            assertNotNull(jobId);
            verify(messageQueueService, times(2)).sendCrawlTask(any(CrawlTaskMessage.class));
        }

        @Test
        @DisplayName("processExcelUploadAsync 任务发送失败应该计数")
        void testAsyncUploadHandlesSendFailure() throws Exception {
            List<String> urls = Arrays.asList(
                "https://www.xiaohongshu.com/explore/abc123",
                "https://www.xiaohongshu.com/explore/def456"
            );
            MockMultipartFile file = createExcelWithUrls(urls);

            when(auditJobRepository.save(any(AuditJob.class))).thenAnswer(invocation -> {
                AuditJob job = invocation.getArgument(0);
                job.setId(1L);
                return job;
            });

            // 第一次成功，第二次失败
            when(messageQueueService.sendCrawlTask(any(CrawlTaskMessage.class)))
                .thenReturn("record-123")
                .thenThrow(new RuntimeException("发送失败"));

            String jobId = excelAuditService.processExcelUploadAsync(file);

            assertNotNull(jobId);
        }
    }

    @Nested
    @DisplayName("结果导出测试")
    class ExportTests {

        @Test
        @DisplayName("不存在的任务应该抛出异常")
        void testExportNonExistentJob() {
            when(auditJobRepository.findByJobId("non-existent-job"))
                .thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                excelAuditService.exportResultsToExcel("non-existent-job");
            });

            assertEquals("ERR_JOB_NOT_FOUND", exception.getCode());
            assertTrue(exception.getMessage().contains("任务不存在"));
        }

        @Test
        @DisplayName("未完成的任务应该抛出异常")
        void testExportIncompleteJob() {
            AuditJob incompleteJob = createMockJob("PROCESSING");

            when(auditJobRepository.findByJobId("test-job"))
                .thenReturn(Optional.of(incompleteJob));

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                excelAuditService.exportResultsToExcel("test-job");
            });

            assertEquals("ERR_JOB_NOT_COMPLETED", exception.getCode());
            assertTrue(exception.getMessage().contains("任务尚未完成"));
        }

        @Test
        @DisplayName("PENDING 状态的任务应该抛出异常")
        void testExportPendingJob() {
            AuditJob pendingJob = createMockJob("PENDING");

            when(auditJobRepository.findByJobId("test-job"))
                .thenReturn(Optional.of(pendingJob));

            assertThrows(BusinessException.class, () -> {
                excelAuditService.exportResultsToExcel("test-job");
            });
        }

        @Test
        @DisplayName("COMPLETED 状态的任务应该能导出")
        void testExportCompletedJob() throws Exception {
            AuditJob completedJob = createMockJob("COMPLETED");
            List<AuditResult> results = List.of(createMockAuditResult("PASSED"));

            when(auditJobRepository.findByJobId("test-job"))
                .thenReturn(Optional.of(completedJob));
            when(auditResultRepository.findByJobId("test-job"))
                .thenReturn(results);

            byte[] excelBytes = excelAuditService.exportResultsToExcel("test-job");

            assertNotNull(excelBytes);
            assertTrue(excelBytes.length > 0);
        }

        @Test
        @DisplayName("PARTIAL_SUCCESS 状态的任务应该能导出")
        void testExportPartialSuccessJob() throws Exception {
            AuditJob partialJob = createMockJob("PARTIAL_SUCCESS");
            List<AuditResult> results = List.of(
                createMockAuditResult("PASSED"),
                createMockAuditResult("REJECTED")
            );

            when(auditJobRepository.findByJobId("test-job"))
                .thenReturn(Optional.of(partialJob));
            when(auditResultRepository.findByJobId("test-job"))
                .thenReturn(results);

            byte[] excelBytes = excelAuditService.exportResultsToExcel("test-job");

            assertNotNull(excelBytes);
            assertTrue(excelBytes.length > 0);
        }

        @Test
        @DisplayName("导出结果应该包含正确的审核数据")
        void testExportContainsCorrectData() throws Exception {
            AuditJob completedJob = createMockJob("COMPLETED");
            AuditResult passedResult = createMockAuditResult("PASSED");
            passedResult.setPostId("abc123");
            passedResult.setConfidenceScore(new BigDecimal("0.95"));

            AuditResult rejectedResult = createMockAuditResult("REJECTED");
            rejectedResult.setPostId("def456");
            rejectedResult.setConfidenceScore(new BigDecimal("0.65"));

            when(auditJobRepository.findByJobId("test-job"))
                .thenReturn(Optional.of(completedJob));
            when(auditResultRepository.findByJobId("test-job"))
                .thenReturn(Arrays.asList(passedResult, rejectedResult));

            byte[] excelBytes = excelAuditService.exportResultsToExcel("test-job");

            assertNotNull(excelBytes);
            // 验证生成的 Excel 内容
            try (ByteArrayInputStream is = new ByteArrayInputStream(excelBytes);
                 Workbook workbook = new XSSFWorkbook(is)) {

                Sheet sheet = workbook.getSheet("审核结果");
                assertNotNull(sheet);

                // 验证有3行（1标题行 + 2数据行）
                assertEquals(2, sheet.getLastRowNum());

                // 验证标题行
                Row headerRow = sheet.getRow(0);
                assertNotNull(headerRow);
                assertEquals("序号", headerRow.getCell(0).getStringCellValue());
                assertEquals("链接", headerRow.getCell(1).getStringCellValue());
            }
        }
    }

    // ===== Helper Methods =====

    private AuditJob createMockJob(String status) {
        AuditJob job = new AuditJob();
        job.setId(1L);
        job.setJobId("test-job");
        job.setStatus(status);
        job.setTotalLinks(10);
        job.setCompletedCount(status.equals("PENDING") ? 0 : 10);
        job.setSuccessCount(status.equals("COMPLETED") ? 10 : 5);
        job.setFailedCount(0);
        job.setFileName("test.xlsx");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }

    private AuditResult createMockAuditResult(String auditStatus) {
        AuditResult result = new AuditResult();
        result.setId(1L);
        result.setJobId("test-job");
        result.setPostId("abc123def456");
        result.setAuditStatus(auditStatus);
        result.setConfidenceScore(new BigDecimal("0.85"));
        result.setAuditedAt(LocalDateTime.now());
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private MockMultipartFile createExcelWithUrls(List<String> urls) throws IOException {
        return createExcelWithUrls(urls, "test.xlsx");
    }

    private MockMultipartFile createExcelWithUrls(List<String> urls, String filename) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("链接");

            // 添加标题行
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("序号");
            headerRow.createCell(1).setCellValue("小红书链接");

            // 添加链接行
            int rowNum = 1;
            for (String url : urls) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rowNum - 1);
                row.createCell(1).setCellValue(url);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            return new MockMultipartFile(
                "file",
                filename,
                VALID_XLSX_CONTENT_TYPE,
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createExcelWithNoLinks() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("测试");

            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("这不是链接");
            row.createCell(1).setCellValue("只是一些文本");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            return new MockMultipartFile(
                "file",
                "no_links.xlsx",
                VALID_XLSX_CONTENT_TYPE,
                outputStream.toByteArray()
            );
        }
    }
}
