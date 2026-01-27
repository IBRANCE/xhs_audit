#!/bin/bash

# 小红书审核系统 - API测试脚本
# 版本: 1.0.0

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

API_BASE="http://localhost:8080"

echo "======================================"
echo "  小红书审核系统 - API测试"
echo "======================================"
echo ""

# 测试函数
test_api() {
    local name=$1
    local method=$2
    local endpoint=$3
    local data=$4
    
    echo -e "${BLUE}[TEST]${NC} $name"
    
    if [ "$method" == "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" "$API_BASE$endpoint")
    elif [ "$method" == "POST" ]; then
        response=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" == "200" ]; then
        echo -e "${GREEN}✓ 成功${NC} (HTTP $http_code)"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    else
        echo -e "${RED}✗ 失败${NC} (HTTP $http_code)"
        echo "$body"
    fi
    echo ""
}

# 1. 健康检查
test_api "健康检查" "GET" "/actuator/health"

# 2. 单条内容审核
echo -e "${BLUE}[TEST]${NC} 单条内容审核"
echo "POST /api/v1/audit/content"
echo '{
  "url": "https://www.xiaohongshu.com/explore/test123",
  "forceRefresh": false
}' | jq '.'
echo ""

# 3. 示例:查询任务状态
echo -e "${BLUE}[INFO]${NC} 查询任务状态示例:"
echo "curl $API_BASE/api/v1/audit/job/\{jobId\}"
echo ""

# 4. 示例:查询审核结果
echo -e "${BLUE}[INFO]${NC} 查询审核结果示例:"
echo "curl $API_BASE/api/v1/audit/result/\{postId\}"
echo ""

# 6. 数据库查询测试
echo -e "${BLUE}[TEST]${NC} 数据库表检查"
psql -U postgres -d xhs_audit -c "\dt" 2>/dev/null && echo -e "${GREEN}✓ 数据库连接正常${NC}" || echo -e "${RED}✗ 数据库连接失败${NC}"
echo ""

echo "======================================"
echo "  测试完成"
echo "======================================"
echo ""
echo "提示: 若要测试文件上传功能,请准备Excel文件后使用:"
echo "curl -X POST $API_BASE/api/v1/audit/upload \\"
echo "  -F 'file=@your-file.xlsx' \\"
echo "  -F 'taskName=测试任务'"
