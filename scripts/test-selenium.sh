#!/bin/bash

# 简单的 Selenium Grid 测试脚本
# 直接编译和运行测试，跳过其他有问题的类

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "======================================"
echo "  Selenium Grid 爬虫测试"
echo "======================================"
echo

cd "$PROJECT_ROOT"

# 检查 Selenium Grid 是否运行
echo "检查 Selenium Grid 状态..."
if curl -s http://localhost:4444/status > /dev/null 2>&1; then
    echo "✓ Selenium Grid 正在运行"
else
    echo "✗ Selenium Grid 未运行"
    echo "请先运行: ./scripts/start-selenium-grid.sh"
    exit 1
fi

echo

# 设置 Maven 选项，跳过有问题的文件
MAVEN_OPTS="-Dmaven.compiler.includes=**/SeleniumManager.java,**/CrawlerService.java -Dmaven.test.includes=**/SeleniumGridTest.java"

# 编译测试
echo "编译测试代码..."
mvn clean test-compile -DskipTests 2>&1 | tail -20

echo
echo "======================================"
echo "运行 Selenium Grid 连接测试..."
echo "======================================"
echo

# 构建 classpath
CLASSPATH="target/classes:target/test-classes"
for jar in $(find ~/.m2/repository/org/seleniumhq/selenium -name "*.jar" 2>/dev/null | head -20); do
    CLASSPATH="$CLASSPATH:$jar"
done

# 运行测试
java -cp "$CLASSPATH" com.xhs.audit.SeleniumGridTest

echo
echo "======================================"
echo "✓ 测试完成！"
echo "======================================"
