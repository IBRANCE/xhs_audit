#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}小红书审核系统 - 清理开发环境${NC}"
echo -e "${GREEN}========================================${NC}"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 1. 停止Docker服务
echo -e "${YELLOW}停止Docker服务...${NC}"
docker-compose down

# 2. 清除本地构建
echo -e "${YELLOW}清除Maven构建文件...${NC}"
rm -rf target/

# 3. 清除缓存
echo -e "${YELLOW}清除本地Maven缓存...${NC}"
rm -rf ~/.m2/repository/com/xhs/ 2>/dev/null || true

echo -e "${GREEN}✓ 清理完成${NC}"
echo ""
echo -e "${YELLOW}下一步:${NC}"
echo "1. 重新启动环境: ./scripts/start-dev.sh"
echo "2. 重新构建项目: mvn clean install"
