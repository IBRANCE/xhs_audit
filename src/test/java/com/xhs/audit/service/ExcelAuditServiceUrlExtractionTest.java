package com.xhs.audit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExcelAuditServiceUrlExtractionTest {

    private static final Path TEMPLATE_PATH = Path.of(System.getProperty("user.home"), "Downloads",
            "audit_import_template.xlsx");

    @Test
    @DisplayName("手工模板链接提取可用")
    void shouldExtractUrlsFromManualTemplate() throws Exception {
        assumeTrue(Files.exists(TEMPLATE_PATH), () -> "测试文件不存在: " + TEMPLATE_PATH);

        ExcelAuditService service = new ExcelAuditService();
        Method extractMethod = ExcelAuditService.class.getDeclaredMethod("extractUrlsFromExcel", InputStream.class);
        extractMethod.setAccessible(true);
        Method isValidMethod = ExcelAuditService.class.getDeclaredMethod("isValidUrl", String.class);
        isValidMethod.setAccessible(true);

        try (InputStream inputStream = Files.newInputStream(TEMPLATE_PATH)) {
            @SuppressWarnings("unchecked")
            List<String> urls = (List<String>) extractMethod.invoke(service, inputStream);

            assertFalse(urls.isEmpty(), "应该至少提取到一条链接");

            List<String> validUrls = new ArrayList<>();
            List<String> invalidUrls = new ArrayList<>();
            for (String url : urls) {
                boolean isValid = (boolean) isValidMethod.invoke(service, url);
                if (isValid) {
                    validUrls.add(url);
                } else {
                    invalidUrls.add(url);
                }
            }

            assertTrue(invalidUrls.isEmpty(), () -> "检测到无效链接: " + invalidUrls);

            System.out.printf("共提取到 %d 条 URL，其中有效 %d 条，无效 %d 条%n", urls.size(),
                    validUrls.size(), invalidUrls.size());
            printUrlSection("✅ 可用链接", validUrls);
            printUrlSection("⚠️ 无效链接", invalidUrls);
        }
    }

    private void printUrlSection(String title, List<String> urls) {
        System.out.printf("%s（%d 条）:%n", title, urls.size());
        if (urls.isEmpty()) {
            return;
        }
        int maxToShow = 20;
        urls.stream()
                .limit(maxToShow)
                .forEach(url -> System.out.println("  - " + url));
        if (urls.size() > maxToShow) {
            System.out.printf("  ... 其余 %d 条已省略%n", urls.size() - maxToShow);
        }
    }
}
