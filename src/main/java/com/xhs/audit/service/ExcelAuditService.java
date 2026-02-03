package com.xhs.audit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.validator.routines.UrlValidator;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;

import jakarta.annotation.PostConstruct;
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

    // 小红书链接正则表达式 - 支持标准链接、短链接和查询参数
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.|m\\.)?(?:xiaohongshu\\.com/(?:explore|discovery/item)/[a-zA-Z0-9_-]+|xhs\\.com/[a-zA-Z0-9_-]+|xhslink\\.com/o/[a-zA-Z0-9]+)(?:\\?[^\\s\"\']*)?");

    private static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[] { "http", "https" },
            UrlValidator.NO_FRAGMENTS);

    private static final Set<String> DEFAULT_ALLOWED_HOSTS = Set.of(
            "www.xiaohongshu.com",
            "xiaohongshu.com",
            "m.xiaohongshu.com",
            "xhs.com",
            "www.xhs.com",
            "xhslink.com");

    private static final Pattern POST_ID_PATTERN = Pattern.compile(
            "/(?:explore|discovery/item)/([a-zA-Z0-9_-]+)");

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @Value("${audit.excel.allowed-hosts:}")
    private String allowedHostsProperty;

    private Set<String> allowedHosts = new LinkedHashSet<>(DEFAULT_ALLOWED_HOSTS);

    /**
     * 处理Excel上传并创建审核任务
     * 
     * @param file 上传的Excel文件
     * @return 任务ID
     */
    public String processExcelUpload(MultipartFile file) {
        try {
            log.info("[同步阶段] 开始处理Excel上传: filename={}, size={}",
                    file.getOriginalFilename(), file.getSize());

            // 1. 验证文件
            validateFile(file);

            // 2. 解析Excel，提取链接
            log.info("[同步阶段] Apache POI解析Excel，提取小红书链接...");
            List<String> urls = extractUrlsFromExcel(file.getInputStream());

            if (urls.isEmpty()) {
                throw new BusinessException("ERR_NO_VALID_LINKS", "Excel中未找到有效的小红书链接");
            }
            log.info("[同步阶段] Excel解析完成: 提取到{}条链接", urls.size());

            // 3. 去重
            Set<String> uniqueUrls = new LinkedHashSet<>(urls);
            log.info("[同步阶段] 链接验证与去重: 原始{}条, 去重后{}条", urls.size(), uniqueUrls.size());

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
            log.info("[同步阶段] 创建审核任务: jobId={}, status=PENDING, totalLinks={}", jobId, uniqueUrls.size());

            // 5. 提交异步任务
            log.info("[同步阶段] 提交后台异步任务: jobId={}", jobId);
            asyncAuditService.processAuditJob(jobId, new ArrayList<>(uniqueUrls));

            log.info("[同步阶段完成] 返回jobId给用户: {}, 用户无需等待，可通过jobId查询进度", jobId);
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
            String url = matcher.group().trim();
            // 清理包裹的引号和尾部标点，避免携带无效字符
            url = url.replaceAll("^[\"'“”‘’\\s]+", "");
            url = url.replaceAll("[\"'“”‘’，。、；：\\s]+$", "");
            if (!url.isEmpty() && isValidUrl(url)) {
                return url;
            }
            log.warn("忽略无效链接: {}", url);
        }
        return null;
    }

    @PostConstruct
    private void configureAllowedHosts() {
        if (allowedHostsProperty == null || allowedHostsProperty.isBlank()) {
            return;
        }

        Set<String> parsedHosts = Arrays.stream(allowedHostsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!parsedHosts.isEmpty()) {
            allowedHosts = parsedHosts;
            log.info("Excel URL白名单已覆盖: {}", allowedHosts);
        }
    }

    private boolean isValidUrl(String url) {
        if (!URL_VALIDATOR.isValid(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return isAllowedHost(uri.getHost());
        } catch (IllegalArgumentException ex) {
            log.debug("URL解析失败: {}", url, ex);
            return false;
        }
    }

    private boolean isAllowedHost(String host) {
        return host != null && allowedHosts.contains(host.toLowerCase());
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
                String[] headers = { "序号", "链接", "审核状态", "车型要求", "内容类型", "话题标签", "内容态度", "图片要求", "置信度", "审核时间" };
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

                    // 提取各类型驳回原因
                    Map<String, String> reasonsMap = parseReasonsByType(result.getReasons());

                    // 车型要求
                    Cell cell3 = dataRow.createCell(3);
                    cell3.setCellValue(reasonsMap.getOrDefault("car_model", ""));
                    cell3.setCellStyle(dataStyle);

                    // 内容类型
                    Cell cell4 = dataRow.createCell(4);
                    cell4.setCellValue(reasonsMap.getOrDefault("content_type", ""));
                    cell4.setCellStyle(dataStyle);

                    // 话题标签
                    Cell cell5 = dataRow.createCell(5);
                    cell5.setCellValue(reasonsMap.getOrDefault("tag", ""));
                    cell5.setCellStyle(dataStyle);

                    // 内容态度
                    Cell cell6 = dataRow.createCell(6);
                    cell6.setCellValue(reasonsMap.getOrDefault("attitude", ""));
                    cell6.setCellStyle(dataStyle);

                    // 图片要求
                    Cell cell7 = dataRow.createCell(7);
                    cell7.setCellValue(reasonsMap.getOrDefault("image", ""));
                    cell7.setCellStyle(dataStyle);

                    // 置信度
                    Cell cell8 = dataRow.createCell(8);
                    if (result.getConfidenceScore() != null) {
                        cell8.setCellValue(result.getConfidenceScore().doubleValue());
                    } else {
                        cell8.setCellValue("-");
                    }
                    cell8.setCellStyle(dataStyle);

                    // 审核时间
                    Cell cell9 = dataRow.createCell(9);
                    if (result.getAuditedAt() != null) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        cell9.setCellValue(result.getAuditedAt().format(formatter));
                    } else {
                        cell9.setCellValue("-");
                    }
                    cell9.setCellStyle(dataStyle);
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

    /**
     * 按类型解析驳回原因
     */
    private Map<String, String> parseReasonsByType(List<Map<String, Object>> reasons) {
        Map<String, String> result = new java.util.HashMap<>();
        result.put("car_model", "");
        result.put("content_type", "");
        result.put("tag", "");
        result.put("attitude", "");
        result.put("image", "");

        if (reasons == null || reasons.isEmpty()) {
            return result;
        }

        for (Map<String, Object> reason : reasons) {
            Object dimensionObj = reason.get("dimension");
            Object reasonObj = reason.get("reason");

            if (dimensionObj == null)
                continue;

            String dimension = dimensionObj.toString();
            String reasonText = reasonObj != null ? reasonObj.toString() : "";

            switch (dimension) {
                case "car_model" -> result.put("car_model", reasonText);
                case "content_type" -> result.put("content_type", reasonText);
                case "tag" -> result.put("tag", reasonText);
                case "attitude" -> result.put("attitude", reasonText);
                case "image" -> result.put("image", reasonText);
            }
        }

        return result;
    }

    /**
     * 下载导入模板
     *
     * @return Excel模板字节数组
     */
    public byte[] downloadTemplate() {
        log.info("生成导入模板文件");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("小红书链接");

            // 创建标题样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle tipStyle = createTipStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // 第1行：标题
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("小红书内容批量审核导入模板");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 2)); // 合并A到C列

            // 第2行：提示
            Row tipRow = sheet.createRow(1);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("请在下方填写需要审核的小红书链接，支持标准链接和短链接");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 2));

            // 第4行：表头
            Row headerRow = sheet.createRow(3);
            String[] headers = { "序号", "小红书链接", "备注(可选)" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 示例数据行（第5-7行）
            String[][] examples = {
                    { "1", "https://www.xiaohongshu.com/explore/6970bf6f000000000e03ce88", "示例：标准链接" },
                    { "2", "https://www.xiaohongshu.com/discovery/item/abc123def456", "示例：另一种格式" },
                    { "3", "https://xhslink.com/o/uEBlswi8i6", "示例：短链接" }
            };

            for (int i = 0; i < examples.length; i++) {
                Row dataRow = sheet.createRow(4 + i);
                for (int j = 0; j < examples[i].length; j++) {
                    Cell cell = dataRow.createCell(j);
                    cell.setCellValue(examples[i][j]);
                    cell.setCellStyle(dataStyle);
                }
            }

            // 设置列宽
            sheet.setColumnWidth(0, 10 * 256); // 序号
            sheet.setColumnWidth(1, 60 * 256); // 链接
            sheet.setColumnWidth(2, 25 * 256); // 备注

            // 写入字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("导入模板生成完成");
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("导入模板生成失败", e);
            throw new BusinessException("ERR_TEMPLATE_GEN_FAILED", "模板生成失败: " + e.getMessage());
        }
    }

    /**
     * 创建提示样式
     */
    private CellStyle createTipStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setItalic(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }
}
