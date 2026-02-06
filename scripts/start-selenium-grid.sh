#!/bin/bash

# 启动 Selenium Grid 的脚本
# 该脚本使用 Docker Compose 启动 Selenium Grid

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
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ 错误: Docker 未安装${NC}"
    exit 1
fi

cd "$PROJECT_ROOT"

# 检查 Selenium Grid 是否已经在运行
if docker ps | grep -q selenium-hub; then
    echo -e "${YELLOW}⚠ Selenium Grid 已经在运行${NC}"
    echo "如需重启，请先运行: make docker-selenium-down"
    exit 0
fi

# 启动 Selenium Grid (8 Firefox Nodes)
echo -e "${GREEN}正在启动 Selenium Grid (Hub + 8 Firefox Nodes)...${NC}"
docker compose -f docker-compose-selenium.yml up -d

# 等待服务启动
echo -e "${YELLOW}等待 Selenium Grid 启动（最多等待 30 秒）...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:4444/status > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Selenium Grid 已成功启动！${NC}"
        echo ""
        echo -e "${GREEN}访问控制台: ${NC}http://localhost:4444/ui"
        echo -e "${GREEN}Grid 状态: ${NC}http://localhost:4444/status"
        echo ""
        echo -e "${YELLOW}可用的 Firefox 节点数量: 8${NC}"
        echo -e "${YELLOW}最大并发会话数: 8${NC}"
        echo ""
        exit 0
    fi
    sleep 1
done

echo -e "${RED}✗ Selenium Grid 启动超时${NC}"
echo "请检查 Docker 日志: docker compose -f docker-compose-selenium.yml logs"
exit 1
