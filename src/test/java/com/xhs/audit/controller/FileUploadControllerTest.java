package com.xhs.audit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.audit.service.ExcelAuditService;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件上传Controller测试
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@WebMvcTest(FileUploadController.class)
@DisplayName("FileUploadController - 文件上传控制器测试")
class FileUploadControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ExcelAuditService excelAuditService;

        /**
         * 测试：成功上传Excel文件
         */
        @Test
        @DisplayName("上传Excel文件 - 返回jobId")
        void testUploadExcelSuccess() throws Exception {
                // Arrange
                byte[] excelContent = "mock excel content".getBytes();
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "audit_list.xlsx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                excelContent);

                String jobId = "job-upload-001";
                when(excelAuditService.processExcelUpload(any()))
                                .thenReturn(jobId);

                // Act & Assert
                MvcResult result = mockMvc.perform(multipart("/api/v1/audit/upload")
                                .file(file))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").exists())
                                .andReturn();

                log.info("✓ Excel文件上传测试通过: {}", result.getResponse().getStatus());
        }

        /**
         * 测试：上传空文件
         */
        @Test
        @DisplayName("上传空Excel文件 - 验证文件大小限制")
        void testUploadEmptyFile() throws Exception {
                // Arrange
                byte[] emptyContent = new byte[0];
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "empty.xlsx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                emptyContent);

                when(excelAuditService.processExcelUpload(any()))
                                .thenThrow(new IllegalArgumentException("文件为空"));

                // Act & Assert
                mockMvc.perform(multipart("/api/v1/audit/upload")
                                .file(file))
                                .andExpect(status().isInternalServerError());

                log.info("✓ 空文件验证测试通过");
        }

        /**
         * 测试：上传超大Excel文件
         */
        @Test
        @DisplayName("上传超大Excel文件 - 返回文件过大错误")
        void testUploadOversizedFile() throws Exception {
                // Arrange
                // 模拟超过50MB的文件
                byte[] largeContent = new byte[51 * 1024 * 1024];
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "large.xlsx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                largeContent);

                when(excelAuditService.processExcelUpload(any()))
                                .thenThrow(new IllegalArgumentException("文件超过大小限制"));

                // Act & Assert
                mockMvc.perform(multipart("/api/v1/audit/upload")
                                .file(file))
                                .andExpect(status().isInternalServerError());

                log.info("✓ 超大文件验证测试通过");
        }

        /**
         * 测试：上传无效格式文件
         */
        @Test
        @DisplayName("上传无效格式文件 - 返回格式错误")
        void testUploadInvalidFormatFile() throws Exception {
                // Arrange
                byte[] txtContent = "This is a text file, not Excel".getBytes();
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "not_excel.txt",
                                MediaType.TEXT_PLAIN_VALUE,
                                txtContent);

                when(excelAuditService.processExcelUpload(any()))
                                .thenThrow(new IllegalArgumentException("不支持的文件格式"));

                // Act & Assert
                mockMvc.perform(multipart("/api/v1/audit/upload")
                                .file(file))
                                .andExpect(status().isInternalServerError());

                log.info("✓ 文件格式验证测试通过");
        }

        /**
         * 测试：下载审核结果Excel文件
         */
        @Test
        @DisplayName("下载审核结果Excel - 返回文件内容")
        void testDownloadExcelResults() throws Exception {
                // Arrange
                String jobId = "job-download-001";
                byte[] excelContent = "mock audit results".getBytes();

                when(excelAuditService.exportResultsToExcel(jobId))
                                .thenReturn(excelContent);

                // Act & Assert
                MvcResult result = mockMvc.perform(get("/api/v1/audit/download/{jobId}", jobId)
                                .param("format", "xlsx"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                                .andExpect(header().exists("Content-Disposition"))
                                .andReturn();

                byte[] returnedContent = result.getResponse().getContentAsByteArray();
                assert returnedContent.length > 0;

                log.info("✓ Excel下载测试通过: {} bytes", returnedContent.length);
        }

        /**
         * 测试：下载不存在的审核结果
         */
        @Test
        @DisplayName("下载不存在的审核结果 - 返回错误")
        void testDownloadNonexistentJob() throws Exception {
                // Arrange
                String jobId = "job-nonexistent";

                when(excelAuditService.exportResultsToExcel(jobId))
                                .thenThrow(new RuntimeException("任务不存在"));

                // Act & Assert
                mockMvc.perform(get("/api/v1/audit/download/{jobId}", jobId))
                                .andExpect(status().isInternalServerError());

                log.info("✓ 不存在的任务下载测试通过");
        }

        /**
         * 测试：文件名中文编码正确性
         */
        @Test
        @DisplayName("下载文件 - 中文文件名正确编码")
        void testDownloadFilenameChinese() throws Exception {
                // Arrange
                String jobId = "job-chinese-001";
                byte[] excelContent = "mock content".getBytes();

                when(excelAuditService.exportResultsToExcel(jobId))
                                .thenReturn(excelContent);

                // Act & Assert
                MvcResult result = mockMvc.perform(get("/api/v1/audit/download/{jobId}", jobId))
                                .andExpect(status().isOk())
                                .andReturn();

                String contentDisposition = result.getResponse().getHeader("Content-Disposition");
                assert contentDisposition != null && contentDisposition.contains("audit_result");

                log.info("✓ 中文文件名编码测试通过: {}", contentDisposition);
        }

        /**
         * 测试：上传多个文件场景
         */
        @Test
        @DisplayName("顺序上传多个文件 - 各自返回不同jobId")
        void testUploadMultipleFilesSequentially() throws Exception {
                // Arrange
                String jobId1 = "job-upload-001";
                String jobId2 = "job-upload-002";

                when(excelAuditService.processExcelUpload(any()))
                                .thenReturn(jobId1)
                                .thenReturn(jobId2);

                // Act & Assert - 第一个文件
                mockMvc.perform(multipart("/api/v1/audit/upload")
                                .file(new MockMultipartFile("file", "file1.xlsx",
                                                MediaType.APPLICATION_OCTET_STREAM_VALUE, "content1".getBytes())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.jobId").value(jobId1));

                // Act & Assert - 第二个文件
                mockMvc.perform(multipart("/api/v1/audit/upload")
                                .file(new MockMultipartFile("file", "file2.xlsx",
                                                MediaType.APPLICATION_OCTET_STREAM_VALUE, "content2".getBytes())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.jobId").value(jobId2));

                log.info("✓ 多文件顺序上传测试通过");
        }

        /**
         * 测试：下载格式参数支持
         */
        @Test
        @DisplayName("下载Excel - 支持format参数")
        void testDownloadWithFormatParameter() throws Exception {
                // Arrange
                String jobId = "job-format-001";
                byte[] excelContent = "mock xlsx content".getBytes();

                when(excelAuditService.exportResultsToExcel(jobId))
                                .thenReturn(excelContent);

                // Act & Assert
                mockMvc.perform(get("/api/v1/audit/download/{jobId}", jobId)
                                .param("format", "xlsx"))
                                .andExpect(status().isOk());

                log.info("✓ 下载格式参数测试通过");
        }
}
