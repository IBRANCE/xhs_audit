package com.xhs.audit;

import java.net.URL;
import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * 独立的 Selenium Grid 连接测试
 * 不依赖 Spring Boot，直接测试 Selenium Grid 是否可用
 * 
 * 运行方式：
 * javac -cp "target/classes:$(mvn dependency:build-classpath
 * -Dmdep.outputFile=/dev/stdout -q)" \
 * src/test/java/com/xhs/audit/SeleniumGridTest.java
 * 
 * java -cp "target/classes:$(mvn dependency:build-classpath
 * -Dmdep.outputFile=/dev/stdout -q):src/test/java" \
 * com.xhs.audit.SeleniumGridTest
 */
public class SeleniumGridTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Selenium Grid 连接测试");
        System.out.println("=".repeat(60));

        String gridUrl = System.getenv().getOrDefault("SELENIUM_GRID_URL", "http://localhost:4444");
        String testUrl = "https://www.xiaohongshu.com/explore/676c8b2c000000001e009b40";

        System.out.println("Grid URL: " + gridUrl);
        System.out.println("测试URL: " + testUrl);
        System.out.println();

        WebDriver driver = null;
        try {
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");

            System.out.println("正在连接 Selenium Grid...");
            driver = new RemoteWebDriver(new URL(gridUrl), options);

            // 设置超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            System.out.println("✓ 已成功连接到 Selenium Grid");
            System.out.println();

            // 导航到页面
            long startTime = System.currentTimeMillis();
            System.out.println("正在加载页面...");
            driver.get(testUrl);

            // 等待内容加载
            Thread.sleep(5000);

            // 获取页面标题
            String title = driver.getTitle();
            String currentUrl = driver.getCurrentUrl();
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("✓ 页面加载成功");
            System.out.println("页面标题: " + title);
            System.out.println("当前URL: " + currentUrl);
            System.out.println("加载耗时: " + duration + " ms");
            System.out.println();

            // 尝试提取内容
            try {
                WebElement contentElement = driver.findElement(By.cssSelector("[class*='content']"));
                String contentText = contentElement.getText();
                System.out.println("✓ 成功提取内容");
                System.out.println("内容长度: " + contentText.length() + " 字符");
                if (contentText.length() > 100) {
                    System.out.println("内容预览: " + contentText.substring(0, 100) + "...");
                }
            } catch (Exception e) {
                System.out.println("⚠ 内容提取失败: " + e.getMessage());
            }

            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("✓ Selenium Grid 测试通过！");
            System.out.println("=".repeat(60));

        } catch (Exception e) {
            System.err.println();
            System.err.println("=".repeat(60));
            System.err.println("✗ Selenium Grid 测试失败");
            System.err.println("=".repeat(60));
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    System.out.println("已关闭浏览器");
                } catch (Exception e) {
                    System.err.println("关闭浏览器失败: " + e.getMessage());
                }
            }
        }
    }
}
