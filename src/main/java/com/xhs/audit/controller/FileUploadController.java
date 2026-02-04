package com.xhs.audit.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.model.dto.ApiResponse;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditJobRepositoryCustom;
import com.xhs.audit.service.ExcelAuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

        @Autowired
        private AuditJobRepository auditJobRepository;

        @Autowired
        private AuditJobRepositoryCustom auditJobRepositoryCustom;

        /**
         * GET /api/v1/audit/jobs - 获取任务列表（分页、筛选）
         */
        @GetMapping("/jobs")
        @Operation(summary = "获取任务列表", description = "分页获取审核任务列表，支持任务ID、状态、文件名和创建时间范围筛选")
        public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> getJobs(
                        @Parameter(description = "页码", example = "0") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "每页数量", example = "10") @RequestParam(defaultValue = "10") int size,
                        @Parameter(description = "任务ID精确匹配") @RequestParam(required = false) String jobId,
                        @Parameter(description = "状态筛选: PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED") @RequestParam(required = false) String status,
                        @Parameter(description = "文件名关键词搜索") @RequestParam(required = false) String keyword,
                        @Parameter(description = "开始时间 (yyyy-MM-dd)") @RequestParam(required = false) String startDate,
                        @Parameter(description = "结束时间 (yyyy-MM-dd)") @RequestParam(required = false) String endDate) {

                log.info("收到任务列表请求: page={}, size={}, jobId={}, status={}, keyword={}, startDate={}, endDate={}",
                                page, size, jobId, status, keyword, startDate, endDate);

                // 构建分页参数
                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

                // 解析时间参数
                LocalDateTime startDateTime = null;
                LocalDateTime endDateTime = null;
                if (startDate != null && !startDate.isEmpty()) {
                        startDateTime = LocalDateTime.parse(startDate + "T00:00:00");
                }
                if (endDate != null && !endDate.isEmpty()) {
                        endDateTime = LocalDateTime.parse(endDate + "T23:59:59");
                }

                // 执行查询（使用Specification动态查询）
                Page<AuditJob> jobPage = auditJobRepositoryCustom.searchJobs(jobId, status, keyword, startDateTime,
                                endDateTime, pageable);

                // 转换为前端需要的格式（使用camelCase以匹配前端期望）
                Page<Map<String, Object>> resultPage = jobPage.map(job -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("jobId", job.getJobId());
                        map.put("fileName", job.getFileName());
                        map.put("totalLinks", job.getTotalLinks());
                        map.put("completedCount", job.getCompletedCount());
                        map.put("passedCount", job.getSuccessCount());
                        map.put("rejectedCount", job.getFailedCount());
                        map.put("status", job.getStatus());
                        map.put("progressPercent", job.getTotalLinks() > 0
                                        ? (job.getCompletedCount() * 100 / job.getTotalLinks())
                                        : 0);
                        map.put("createdAt", job.getCreatedAt());
                        return map;
                });

                return ResponseEntity.ok(ApiResponse.success(resultPage));
        }

        /**
         * GET /api/v1/audit/jobs/{jobId} - 获取任务详情
         */
        @GetMapping("/jobs/{jobId}")
        @Operation(summary = "获取任务详情", description = "获取指定任务的详细信息")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getJobDetail(
                        @Parameter(description = "任务ID", required = true) @PathVariable String jobId,
                        @Parameter(description = "详情页码", example = "0") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "详情每页数量", example = "20") @RequestParam(defaultValue = "20") int size) {

                log.info("收到任务详情请求: jobId={}, page={}, size={}", jobId, page, size);

                // 查询任务
                AuditJob job = auditJobRepository.findByJobId(jobId)
                                .orElseThrow(() -> new BusinessException("ERR_JOB_NOT_FOUND", "任务不存在: " + jobId));

                // 构建任务信息（使用camelCase以匹配前端期望）
                Map<String, Object> jobInfo = new HashMap<>();
                jobInfo.put("jobId", job.getJobId());
                jobInfo.put("fileName", job.getFileName());
                jobInfo.put("totalLinks", job.getTotalLinks());
                jobInfo.put("completedCount", job.getCompletedCount());
                jobInfo.put("passedCount", job.getSuccessCount());
                jobInfo.put("rejectedCount", job.getFailedCount());
                jobInfo.put("status", job.getStatus());
                jobInfo.put("progressPercent", job.getTotalLinks() > 0
                                ? (job.getCompletedCount() * 100 / job.getTotalLinks())
                                : 0);
                jobInfo.put("createdAt", job.getCreatedAt());
                jobInfo.put("updatedAt", job.getUpdatedAt());

                return ResponseEntity.ok(ApiResponse.success(jobInfo));
        }

        /**
         * POST /api/v1/audit/upload - 上传Excel文件（同步模式）
         */
        @PostMapping("/upload")
        @Operation(summary = "上传Excel文件（同步）", description = "上传包含小红书链接的Excel文件进行批量审核（同步模式）")
        public ResponseEntity<ApiResponse<Map<String, Object>>> uploadExcel(
                        @Parameter(description = "Excel文件", required = true) @RequestParam("file") MultipartFile file) {

                log.info("收到Excel上传请求（同步模式）: filename={}, size={}",
                                file.getOriginalFilename(), file.getSize());

                String jobId = excelAuditService.processExcelUpload(file);

                Map<String, Object> response = new HashMap<>();
                response.put("jobId", jobId);
                response.put("message", "文件上传成功，审核任务已创建");
                response.put("mode", "sync");

                return ResponseEntity.ok(com.xhs.audit.model.dto.ApiResponse.success(response));
        }

        /**
         * POST /api/v1/audit/upload-async - 上传Excel文件（异步Redis模式）
         */
        @PostMapping("/upload-async")
        @Operation(summary = "上传Excel文件（异步）", description = "上传包含小红书链接的Excel文件进行批量审核（Redis Stream异步模式）")
        public ResponseEntity<ApiResponse<Map<String, Object>>> uploadExcelAsync(
                        @Parameter(description = "Excel文件", required = true) @RequestParam("file") MultipartFile file) {

                log.info("收到Excel上传请求（异步Redis模式）: filename={}, size={}",
                                file.getOriginalFilename(), file.getSize());

                String jobId = excelAuditService.processExcelUploadAsync(file);

                Map<String, Object> response = new HashMap<>();
                response.put("jobId", jobId);
                response.put("message", "文件上传成功，任务已提交到Redis队列，请在任务列表查看进度");
                response.put("mode", "async");
                response.put("checkUrl", "/api/v1/audit/jobs/" + jobId);

                return ResponseEntity.ok(com.xhs.audit.model.dto.ApiResponse.success(response));
        }

        /**
         * GET /api/v1/audit/download/{jobId} - 下载审核结果Excel
         */
        @GetMapping("/download/{jobId}")
        @Operation(summary = "下载审核结果", description = "下载指定任务的审核结果Excel文件")
        public ResponseEntity<byte[]> downloadExcel(
                        @Parameter(description = "任务ID", required = true) @PathVariable String jobId,
                        @Parameter(description = "文件格式", example = "xlsx") @RequestParam(defaultValue = "xlsx") String format) {

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
