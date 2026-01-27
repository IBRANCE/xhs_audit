package com.xhs.audit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Excel审核服务
 * 处理Excel文件上传、解析、导出
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Service
public class ExcelAuditService {

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired
    private AuditResultRepository auditResultRepository;

    @Autowired
    private AsyncAuditService asyncAuditService;

    // 小红书链接正则表达式
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.|m\\.)?(?:xiaohongshu\\.com/explore/[a-zA-Z0-9_-]+|xhs\\.com/[a-zA-Z0-9_-]+)");

    private static final Pattern POST_ID_PATTERN = Pattern.compile(
            "/explore/([a-zA-Z0-9_-]+)");

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    /**
     * 处理Excel上传并创建审核任务
     * 
     * @param file 上传的Excel文件
     * @return 任务ID
     */
    public String processExcelUpload(MultipartFile file) {
        try {
            log.info("开始处理Excel上传: filename={}, size={}",
                    file.getOriginalFilename(), file.getSize());

            // 1. 验证文件
            validateFile(file);

            // 2. 解析Excel，提取链接
            List<String> urls = extractUrlsFromExcel(file.getInputStream());

            if (urls.isEmpty()) {
                throw new BusinessException("ERR_NO_VALID_LINKS", "Excel中未找到有效的小红书链接");
            }

            // 3. 去重
            Set<String> uniqueUrls = new LinkedHashSet<>(urls);
            log.info("链接去重: 原始{}条, 去重后{}条", urls.size(), uniqueUrls.size());

            // 4. 创建审核任务
            String jobId = generateJobId();
            AuditJob job = new AuditJob();
            job.setJobId(jobId);
            job.setTotalLinks(uniqueUrls.size());
            job.setCompletedCount(0);
            job.setSuccessCount(0);
            job.setFailedCount(0);
            job.setStatus("PENDING");
            job.setFileName(file.getOriginalFilename());
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());

            auditJobRepository.save(job);
            log.info("审核任务已创建: jobId={}, totalLinks={}", jobId, uniqueUrls.size());

            // 5. 提交异步任务
            asyncAuditService.processAuditJob(jobId, new ArrayList<>(uniqueUrls));

            return jobId;

        } catch (IOException e) {
            log.error("Excel文件处理失败", e);
            throw new BusinessException("ERR_FILE_PARSE_FAILED", "Excel文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("ERR_FILE_NOT_FOUND", "文件未上传");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("ERR_FILE_SIZE_EXCEED", "文件大小超过50MB限制");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException("ERR_FILE_FORMAT", "仅支持.xlsx和.xls格式");
        }
    }

    /**
     * 从Excel中提取小红书链接
     */
    private List<String> extractUrlsFromExcel(InputStream inputStream) throws IOException {
        List<String> urls = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            // 遍历所有sheet
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                log.info("解析Sheet: {}", sheet.getSheetName());

                // 遍历所有行
                for (Row row : sheet) {
                    if (row == null)
                        continue;

                    // 遍历所有列
                    for (Cell cell : row) {
                        if (cell == null)
                            continue;

                        String cellValue = getCellValueAsString(cell);
                        if (cellValue != null && !cellValue.isEmpty()) {
                            // 提取链接
                            String url = extractUrl(cellValue);
                            if (url != null) {
                                urls.add(url);
                            }
                        }
                    }
                }
            }
        }

        log.info("从Excel中提取了{}条链接", urls.size());
        return urls;
    }

    /**
     * 获取单元格值（转为字符串）
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    /**
     * 从文本中提取小红书链接
     */
    private String extractUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            String url = matcher.group();
            // 清理尾部中文标点
            url = url.replaceAll("[，。、；：]+$", "");
            return url;
        }
        return null;
    }

    /**
     * 生成任务ID
     */
    private String generateJobId() {
        return "job-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                + "-" + UUID.randomUUID().toString();
    }

    /**
     * 导出审核结果为Excel
     * 
     * @param jobId 任务ID
     * @return Excel字节数组
     */
    public byte[] exportResultsToExcel(String jobId) {
        try {
            log.info("导出审核结果: jobId={}", jobId);

            // 查询任务
            AuditJob job = auditJobRepository.findByJobId(jobId)
                    .orElseThrow(() -> new BusinessException("ERR_JOB_NOT_FOUND", "任务不存在"));

            // 检查任务状态
            if (!"COMPLETED".equals(job.getStatus()) && !"PARTIAL_SUCCESS".equals(job.getStatus())) {
                throw new BusinessException("ERR_JOB_NOT_COMPLETED", "任务尚未完成，无法下载结果");
            }

            // 创建Excel
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("审核结果");

                // 创建标题样式
                CellStyle headerStyle = createHeaderStyle(workbook);

                // 创建标题行
                Row headerRow = sheet.createRow(0);
                String[] headers = { "序号", "链接", "审核状态", "驳回原因", "置信度", "审核时间" };
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // 查询审核结果并填充数据
                List<AuditResult> results = auditResultRepository.findByJobId(jobId);
                log.info("查询到{}条审核结果", results.size());

                // 创建数据样式
                CellStyle dataStyle = createDataStyle(workbook);
                CellStyle passStyle = createPassStyle(workbook);
                CellStyle rejectStyle = createRejectStyle(workbook);

                int rowIndex = 1;
                for (AuditResult result : results) {
                    Row dataRow = sheet.createRow(rowIndex++);

                    // 序号
                    Cell cell0 = dataRow.createCell(0);
                    cell0.setCellValue(rowIndex - 1);
                    cell0.setCellStyle(dataStyle);

                    // 链接
                    Cell cell1 = dataRow.createCell(1);
                    cell1.setCellValue("https://www.xiaohongshu.com/explore/" + result.getPostId());
                    cell1.setCellStyle(dataStyle);

                    // 审核状态
                    Cell cell2 = dataRow.createCell(2);
                    String statusText = switch (result.getAuditStatus()) {
                        case "PASSED" -> "通过";
                        case "REJECTED" -> "驳回";
                        case "UNCERTAIN" -> "待定";
                        default -> result.getAuditStatus();
                    };
                    cell2.setCellValue(statusText);
                    cell2.setCellStyle("PASSED".equals(result.getAuditStatus()) ? passStyle
                            : "REJECTED".equals(result.getAuditStatus()) ? rejectStyle : dataStyle);

                    // 驳回原因
                    Cell cell3 = dataRow.createCell(3);
                    String reasonText = formatReasons(result.getReasons());
                    cell3.setCellValue(reasonText);
                    cell3.setCellStyle(dataStyle);

                    // 置信度
                    Cell cell4 = dataRow.createCell(4);
                    if (result.getConfidenceScore() != null) {
                        cell4.setCellValue(result.getConfidenceScore().doubleValue());
                    } else {
                        cell4.setCellValue("-");
                    }
                    cell4.setCellStyle(dataStyle);

                    // 审核时间
                    Cell cell5 = dataRow.createCell(5);
                    if (result.getAuditedAt() != null) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        cell5.setCellValue(result.getAuditedAt().format(formatter));
                    } else {
                        cell5.setCellValue("-");
                    }
                    cell5.setCellStyle(dataStyle);
                }

                // 自动调整列宽
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                // 写入字节数组
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                workbook.write(outputStream);

                log.info("Excel导出完成: jobId={}", jobId);
                return outputStream.toByteArray();
            }

        } catch (IOException e) {
            log.error("Excel导出失败", e);
            throw new BusinessException("ERR_EXPORT_FAILED", "Excel导出失败: " + e.getMessage());
        }
    }

    /**
     * 创建标题样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // 字体
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);

        // 背景色
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // 边框
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        // 居中
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    /**
     * 创建数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // 边框
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        // 对齐
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    /**
     * 创建通过状态样式（绿色背景）
     */
    private CellStyle createPassStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 创建驳回状态样式（红色背景）
     */
    private CellStyle createRejectStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 格式化驳回原因
     */
    private String formatReasons(List<Map<String, Object>> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "-";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size(); i++) {
            Map<String, Object> reason = reasons.get(i);
            if (i > 0)
                sb.append("; ");
            sb.append(reason.get("dimension")).append(": ").append(reason.get("reason"));
        }
        return sb.toString();
    }
}
