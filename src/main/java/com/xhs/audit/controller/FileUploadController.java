package com.xhs.audit.controller;

import java.net.URLEncoder;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import com.xhs.audit.model.dto.ApiResponse;
import com.xhs.audit.service.ExcelAuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件上传Controller
 * 处理Excel文件上传和下载
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Tag(name = "文件管理", description = "Excel文件上传和下载相关接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
public class FileUploadController {

        @Autowired
        private ExcelAuditService excelAuditService;

        /**
         * POST /api/v1/audit/upload - 上传Excel文件
                @Operation(summary = "上传Excel文件", description = "上传包含小红书链接的Excel文件进行批量审核")
         */
        @PostMapping("/upload")
        public ResponseEntity<ApiResponse<Map<String, Object>>> uploadExcel(
                                                @Parameter(description = "Excel文件", required = true)
                        @RequestParam("file") MultipartFile file) {

                log.info("收到Excel上传请求: filename={}, size={}",
                                file.getOriginalFilename(), file.getSize());

                String jobId = excelAuditService.processExcelUpload(file);

                Map<String, Object> response = new HashMap<>();
                response.put("jobId", jobId);
                response.put("message", "文件上传成功，审核任务已创建");

                return ResponseEntity.ok(com.xhs.audit.model.dto.ApiResponse.success(response));
        }

        /**
         * GET /api/v1/audit/download/{jobId} - 下载审核结果Excel
         */
        @GetMapping("/download/{jobId}")
        @Operation(summary = "下载审核结果", description = "下载指定任务的审核结果Excel文件")
        public ResponseEntity<byte[]> downloadExcel(
                        @Parameter(description = "任务ID", required = true)
                        @PathVariable String jobId,
                        @Parameter(description = "文件格式", example = "xlsx")
                        @RequestParam(defaultValue = "xlsx") String format) {

                log.info("收到Excel下载请求: jobId={}, format={}", jobId, format);

                byte[] excelBytes = excelAuditService.exportResultsToExcel(jobId);

                String filename = "audit_result_" + jobId + ".xlsx";
                String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                                .replaceAll("\\+", "%20");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", encodedFilename);
                headers.add("Access-Control-Expose-Headers", "Content-Disposition");

                return ResponseEntity.ok()
                                .headers(headers)
                                .body(excelBytes);
        }

        /**
         * GET /api/v1/audit/template - 下载导入模板
         */
        @GetMapping("/template")
        @Operation(summary = "下载导入模板", description = "下载Excel批量审核导入模板文件")
        public ResponseEntity<byte[]> downloadTemplate() {
                log.info("收到导入模板下载请求");

                byte[] templateBytes = excelAuditService.downloadTemplate();

                String filename = "audit_import_template.xlsx";
                String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                                .replaceAll("\\+", "%20");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", encodedFilename);
                headers.add("Access-Control-Expose-Headers", "Content-Disposition");

                return ResponseEntity.ok()
                                .headers(headers)
                                .body(templateBytes);
        }
}
