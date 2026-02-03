#!/bin/bash

# 启动 Selenium Grid 的脚本
# 该脚本支持两种模式：
# 1. Docker 模式（推荐）：使用 Docker Compose 启动 Selenium Grid
# 2. Standalone 模式：使用 Selenium Standalone Server JAR 启动

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}==================================="
echo "  启动 Selenium Grid"
echo -e "===================================${NC}"

# 检查 Docker 是否安装
if command -v docker &> /dev/null && command -v docker-compose &> /dev/null; then
    echo -e "${GREEN}✓ 检测到 Docker，使用 Docker Compose 模式${NC}"
    
    cd "$PROJECT_ROOT"
    
    # 检查 Selenium Grid 是否已经在运行
    if docker ps | grep -q selenium-hub; then
        echo -e "${YELLOW}⚠ Selenium Grid 已经在运行${NC}"
        echo "如需重启，请先运行: docker-compose -f docker-compose-selenium.yml down"
        exit 0
    fi
    
    # 启动 Selenium Grid
    echo -e "${GREEN}正在启动 Selenium Grid (Hub + 4 Chrome Nodes)...${NC}"
    docker-compose -f docker-compose-selenium.yml up -d
    
    # 等待服务启动
    echo -e "${YELLOW}等待 Selenium Grid 启动（最多等待 30 秒）...${NC}"
    for i in {1..30}; do
        if curl -s http://localhost:4444/status > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Selenium Grid 已成功启动！${NC}"
            echo ""
            echo -e "${GREEN}访问控制台: ${NC}http://localhost:4444/ui"
            echo -e "${GREEN}Grid 状态: ${NC}http://localhost:4444/status"
            echo ""
            echo -e "${YELLOW}可用的 Chrome 节点数量: 4${NC}"
            echo -e "${YELLOW}最大并发会话数: 4${NC}"
            echo ""
            exit 0
        fi
        sleep 1
    done
    
    echo -e "${RED}✗ Selenium Grid 启动超时${NC}"
    echo "请检查 Docker 日志: docker-compose -f docker-compose-selenium.yml logs"
    exit 1

else
    echo -e "${YELLOW}⚠ 未检测到 Docker，尝试使用 Standalone 模式${NC}"
    
    # 检查 Java 是否安装
    if ! command -v java &> /dev/null; then
        echo -e "${RED}✗ 错误: 需要安装 Java${NC}"
        echo "请先安装 Java 11+ 或使用 Docker 模式"
        exit 1
    fi
    
    SELENIUM_VERSION="4.18.1"
    SELENIUM_JAR="$PROJECT_ROOT/selenium-server-${SELENIUM_VERSION}.jar"
    
    # 下载 Selenium Standalone Server（如果不存在）
    if [ ! -f "$SELENIUM_JAR" ]; then
        echo -e "${YELLOW}下载 Selenium Standalone Server ${SELENIUM_VERSION}...${NC}"
        curl -L "https://github.com/SeleniumHQ/selenium/releases/download/selenium-${SELENIUM_VERSION}/selenium-server-${SELENIUM_VERSION}.jar" \
            -o "$SELENIUM_JAR"
    fi
    
    # 检查是否已经在运行
    if lsof -i:4444 > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠ 端口 4444 已被占用，Selenium Grid 可能已在运行${NC}"
        exit 0
    fi
    
    # 启动 Selenium Standalone Server
    echo -e "${GREEN}正在启动 Selenium Standalone Server...${NC}"
    java -jar "$SELENIUM_JAR" standalone --port 4444 > "$PROJECT_ROOT/logs/selenium.log" 2>&1 &
    
    # 等待服务启动
    echo -e "${YELLOW}等待 Selenium Server 启动（最多等待 30 秒）...${NC}"
    for i in {1..30}; do
        if curl -s http://localhost:4444/status > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Selenium Standalone Server 已成功启动！${NC}"
            echo ""
            echo -e "${GREEN}访问控制台: ${NC}http://localhost:4444"
            echo ""
            exit 0
        fi
        sleep 1
    done
    
    echo -e "${RED}✗ Selenium Server 启动超时${NC}"
    echo "请检查日志: $PROJECT_ROOT/logs/selenium.log"
    exit 1
fi
