#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}小红书审核系统 - 开发环境启动脚本${NC}"
echo -e "${GREEN}========================================${NC}"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 1. 启动Docker服务
echo -e "\n${YELLOW}[1/4] 启动Docker服务...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ Docker未安装${NC}"
    exit 1
fi

# 启动服务，如果已运行则跳过
docker-compose up -d 2>/dev/null || true
sleep 5

# 2. 检查数据库连接
echo -e "\n${YELLOW}[2/4] 检查数据库连接...${NC}"
if docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then
    echo -e "${GREEN}✓ PostgreSQL连接成功${NC}"
else
    echo -e "${RED}✗ PostgreSQL连接失败，等待30秒...${NC}"
    sleep 30
    if ! docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then
        echo -e "${RED}✗ PostgreSQL启动超时${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ PostgreSQL连接成功${NC}"
fi

# 3. 检查Redis连接
echo -e "\n${YELLOW}[3/4] 检查Redis连接...${NC}"
if docker-compose exec -T redis redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Redis连接成功${NC}"
else
    echo -e "${RED}✗ Redis连接失败，等待30秒...${NC}"
    sleep 30
    if ! docker-compose exec -T redis redis-cli ping > /dev/null 2>&1; then
        echo -e "${RED}✗ Redis启动超时${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Redis连接成功${NC}"
fi

# 4. Maven构建和运行
echo -e "\n${YELLOW}[4/4] Maven构建和启动应用...${NC}"

if [ "$1" == "build" ]; then
    echo "执行完整构建..."
    mvn clean install
elif [ "$1" == "skip" ]; then
    echo "跳过编译"
else
    echo "执行快速编译..."
    mvn clean compile
fi

echo "启动Spring Boot应用..."
exec mvn spring-boot:run
