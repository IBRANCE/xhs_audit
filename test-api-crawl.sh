#!/bin/bash

# 测试小红书链接抓取
# 通过 Spring Boot 应用的 API 进行测试

echo "======================================"
echo "  小红书链接抓取测试 (通过API)"
echo "======================================"
echo

TEST_URL="https://www.xiaohongshu.com/explore/69719680000000000e00c876?xsec_token=ABwPNEro_Ghzgt5TjjrKPDKAU6bFEACIK5jgL2mB1kHv0=&xsec_source=pc_user"

echo "测试URL: $TEST_URL"
echo

# 检查应用是否运行
echo "检查应用状态..."
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "⚠ 应用未运行，需要先启动应用"
    echo
    echo "请运行以下命令启动应用:"
    echo "  cd /Users/bruce/Workspace/code/xhs_audit"
    echo "  ./start-local.sh"
    echo
    echo "如果只想测试 Selenium Grid 连接，可以直接运行 Java 测试..."
    echo
    
    # 使用简化的 Java 测试
    echo "======================================"
    echo "  使用直接 Java 测试"
    echo "======================================"
    echo
    
    # 检查 Selenium Grid
    if ! curl -s http://localhost:4444/status > /dev/null 2>&1; then
        echo "✗ Selenium Grid 未运行"
        echo "请运行: ./scripts/start-selenium-grid.sh"
        exit 1
    fi
    
    echo "✓ Selenium Grid 正在运行"
    echo
    
    # 创建简化的测试
    cat > /tmp/SimpleCrawlTest.java << 'JAVAEOF'
import java.net.URL;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

public class SimpleCrawlTest {
    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "https://www.xiaohongshu.com/explore/676c8b2c000000001e009b40";
        
        System.out.println("连接 Selenium Grid...");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        
        WebDriver driver = new RemoteWebDriver(new URL("http://localhost:4444"), options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        
        System.out.println("✓ 已连接");
        System.out.println("访问: " + url);
        
        long start = System.currentTimeMillis();
        driver.get(url);
        Thread.sleep(3000);
        
        System.out.println("✓ 页面加载完成 (" + (System.currentTimeMillis() - start) + "ms)");
        System.out.println("标题: " + driver.getTitle());
        System.out.println("URL: " + driver.getCurrentUrl());
        
        try {
            String text = driver.findElement(By.cssSelector("[class*='content']")).getText();
            System.out.println("内容: " + (text.length() > 100 ? text.substring(0, 100) + "..." : text));
        } catch (Exception e) {
            System.out.println("内容提取: 未找到");
        }
        
        driver.quit();
        System.out.println("\n✓ 测试完成！");
    }
}
JAVAEOF
    
    echo "编译测试..."
    cd /Users/bruce/Workspace/code/xhs_audit
    
    # 简化的 classpath
    CP="target/classes"
    for jar in ~/.m2/repository/org/seleniumhq/selenium/**/4.18.1/*.jar; do
        [ -f "$jar" ] && CP="$CP:$jar"
    done
    
    javac -cp "$CP" /tmp/SimpleCrawlTest.java
    
    if [ $? -eq 0 ]; then
        echo "✓ 编译成功"
        echo
        echo "运行测试..."
        echo "======================================"
        java -cp "$CP:/tmp" SimpleCrawlTest "$TEST_URL"
        echo "======================================"
    else
        echo "✗ 编译失败"
        exit 1
    fi
    
    exit 0
fi

echo "✓ 应用正在运行"
echo

# 通过 API 测试
echo "通过 API 测试抓取..."
echo "======================================"
echo

RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/audit/crawl" \
  -H "Content-Type: application/json" \
  -d "{\"url\": \"$TEST_URL\"}" \
  -w "\nHTTP_CODE:%{http_code}")

HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE" | cut -d: -f2)
BODY=$(echo "$RESPONSE" | sed '/HTTP_CODE/d')

echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"

echo
echo "======================================"

if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ 抓取成功！"
else
    echo "✗ 抓取失败 (HTTP $HTTP_CODE)"
fi

echo "======================================"
