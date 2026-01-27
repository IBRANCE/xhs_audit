#!/bin/bash

# 检查系统和环境配置
set -e

echo "=========================================="
echo "XHS Audit - 环境检查"
echo "=========================================="

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

check_command() {
    if command -v "$1" &> /dev/null; then
        version=$($1 --version 2>&1 | head -1)
        echo -e "${GREEN}✓${NC} $1: $version"
        return 0
    else
        echo -e "${RED}✗${NC} $1: 未安装"
        return 1
    fi
}

echo ""
echo "1. 检查必要工具..."
check_command java
check_command mvn
check_command git
check_command docker || true
check_command docker-compose || true

echo ""
echo "2. 检查项目文件..."
if [ -f "pom.xml" ]; then
    echo -e "${GREEN}✓${NC} pom.xml 存在"
else
    echo -e "${RED}✗${NC} pom.xml 不存在"
    exit 1
fi

if [ -f ".gitignore" ]; then
    echo -e "${GREEN}✓${NC} .gitignore 存在"
fi

if [ -d "src" ]; then
    echo -e "${GREEN}✓${NC} src 目录存在"
    file_count=$(find src -name "*.java" | wc -l)
    echo -e "  ${YELLOW}→${NC} Java文件数: $file_count"
fi

echo ""
echo "3. 检查Docker容器..."
if command -v docker &> /dev/null; then
    if docker ps &> /dev/null; then
        echo -e "${GREEN}✓${NC} Docker 可用"
        
        if docker-compose ps | grep -q postgres; then
            echo -e "${GREEN}✓${NC} PostgreSQL 容器运行中"
        else
            echo -e "${YELLOW}⚠${NC} PostgreSQL 容器未运行"
        fi
        
        if docker-compose ps | grep -q redis; then
            echo -e "${GREEN}✓${NC} Redis 容器运行中"
        else
            echo -e "${YELLOW}⚠${NC} Redis 容器未运行"
        fi
    else
        echo -e "${RED}✗${NC} Docker 守护进程未运行"
    fi
else
    echo -e "${YELLOW}⚠${NC} Docker 未安装"
fi

echo ""
echo "4. 检查Maven依赖..."
if [ -d "target" ]; then
    echo -e "${GREEN}✓${NC} target 目录存在（已编译）"
else
    echo -e "${YELLOW}⚠${NC} target 目录不存在（未编译）"
fi

echo ""
echo "=========================================="
echo "环境检查完成！"
echo ""
echo "建议的下一步："
echo "1. 启动Docker: bash scripts/docker-up.sh"
echo "2. 编译项目:   mvn clean compile"
echo "3. 运行应用:   mvn spring-boot:run"
echo "=========================================="
