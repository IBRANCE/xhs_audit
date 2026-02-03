#!/bin/bash

# 简单的爬虫测试脚本
# 直接使用 Java + Selenium 抓取小红书链接

set -e

echo "======================================"
echo "  小红书链接抓取测试"
echo "======================================"
echo

# 测试URL
TEST_URL="https://www.xiaohongshu.com/explore/69719680000000000e00c876?xsec_token=ABwPNEro_Ghzgt5TjjrKPDKAU6bFEACIK5jgL2mB1kHv0=&xsec_source=pc_user"

echo "测试URL: $TEST_URL"
echo

# 检查 Selenium Grid
echo "检查 Selenium Grid 状态..."
if ! curl -s http://localhost:4444/status > /dev/null 2>&1; then
    echo "✗ Selenium Grid 未运行"
    echo "请先运行: ./scripts/start-selenium-grid.sh"
    exit 1
fi
echo "✓ Selenium Grid 正在运行"
echo

# 创建临时测试类
cat > /tmp/TestCrawl.java << 'EOF'
import java.net.URL;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestCrawl {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java TestCrawl <url>");
            System.exit(1);
        }
        
        String testUrl = args[0];
        WebDriver driver = null;
        
        try {
            System.out.println("正在连接 Selenium Grid...");
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            driver = new RemoteWebDriver(new URL("http://localhost:4444"), options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            
            System.out.println("✓ 已连接到 Selenium Grid");
            System.out.println();
            
            long startTime = System.currentTimeMillis();
            System.out.println("正在加载页面: " + testUrl);
            driver.get(testUrl);
            
            // 等待页面加载
            Thread.sleep(5000);
            
            String finalUrl = driver.getCurrentUrl();
            String title = driver.getTitle();
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.println("✓ 页面加载成功");
            System.out.println("页面标题: " + title);
            System.out.println("当前URL: " + finalUrl);
            System.out.println("加载耗时: " + duration + " ms");
            System.out.println();
            
            // 尝试提取标题
            System.out.println("开始提取内容...");
            System.out.println("-".repeat(60));
            
            String[] titleSelectors = {
                "#detail-title",
                "[class*='title']",
                "h1"
            };
            
            String extractedTitle = null;
            for (String selector : titleSelectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                    if (!elements.isEmpty()) {
                        extractedTitle = elements.get(0).getText();
                        if (extractedTitle != null && !extractedTitle.trim().isEmpty()) {
                            System.out.println("标题 (选择器: " + selector + "): " + extractedTitle);
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                }
            }
            
            // 尝试提取正文
            String[] contentSelectors = {
                "#detail-desc > span > span",
                "[class*='desc']",
                "[class*='note-content']",
                "[class*='content']"
            };
            
            for (String selector : contentSelectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                    if (!elements.isEmpty()) {
                        String content = elements.get(0).getText();
                        if (content != null && !content.trim().isEmpty()) {
                            System.out.println("正文 (选择器: " + selector + "): ");
                            System.out.println(content.length() > 200 ? content.substring(0, 200) + "..." : content);
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 继续
                }
            }
            
            // 提取图片
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object imagesObj = js.executeScript(
                "return Array.from(document.querySelectorAll('img')).map(img => img.src).filter(src => src && !src.includes('avatar')).slice(0, 3);"
            );
            
            if (imagesObj instanceof List) {
                List<?> images = (List<?>) imagesObj;
                System.out.println("图片数量: " + images.size());
                for (int i = 0; i < Math.min(3, images.size()); i++) {
                    System.out.println("  - " + images.get(i));
                }
            }
            
            System.out.println("-".repeat(60));
            System.out.println();
            System.out.println("✓ 抓取测试完成！");
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("✗ 抓取失败");
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("已关闭浏览器");
            }
        }
    }
}
EOF

echo "编译测试类..."
cd /Users/bruce/Workspace/code/xhs_audit

# 构建 classpath
CLASSPATH="target/classes:target/test-classes"
for jar in ~/.m2/repository/org/seleniumhq/selenium/selenium-remote-driver/4.18.1/*.jar; do
    [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done
for jar in ~/.m2/repository/org/seleniumhq/selenium/selenium-api/4.18.1/*.jar; do
    [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done
for jar in ~/.m2/repository/org/seleniumhq/selenium/selenium-chrome-driver/4.18.1/*.jar; do
    [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done
for jar in ~/.m2/repository/org/seleniumhq/selenium/selenium-support/4.18.1/*.jar; do
    [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done

# 添加必要的依赖
CLASSPATH="$CLASSPATH:$(find ~/.m2/repository/com/google/guava -name "guava-*.jar" | head -1)"
CLASSPATH="$CLASSPATH:$(find ~/.m2/repository/org/apache/httpcomponents/client5 -name "httpclient5-*.jar" | head -1)"
CLASSPATH="$CLASSPATH:$(find ~/.m2/repository/org/apache/httpcomponents/core5 -name "httpcore5-*.jar" | head -1)"

javac -cp "$CLASSPATH" /tmp/TestCrawl.java 2>&1 | tail -10

if [ $? -ne 0 ]; then
    echo "编译失败"
    exit 1
fi

echo "✓ 编译成功"
echo

echo "开始抓取测试..."
echo "======================================"
echo

java -cp "$CLASSPATH:/tmp" TestCrawl "$TEST_URL"

echo
echo "======================================"
echo "测试完成！"
echo "======================================"
