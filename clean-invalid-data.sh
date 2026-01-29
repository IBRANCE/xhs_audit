#!/bin/bash

# 清理数据库中的无效内容数据
# 使用方式: ./clean-invalid-data.sh

echo "======================================"
echo "清理数据库中的无效内容"
echo "======================================"
echo ""

# 检查服务是否运行
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health | grep -q "200"; then
    echo "✗ 错误: 服务未运行，请先启动应用"
    echo "  运行: mvn spring-boot:run -Dspring-boot.run.profiles=local"
    exit 1
fi

echo "✓ 服务运行正常"
echo ""
echo "开始清理无效数据..."
echo ""

# 调用清理API
response=$(curl -s -X POST http://localhost:8080/api/admin/clean-invalid-contents \
    -H "Content-Type: application/json")

# 检查响应
if echo "$response" | grep -q "totalCount"; then
    echo "✓ 清理完成！"
    echo ""
    echo "清理结果:"
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
else
    echo "✗ 清理失败"
    echo "错误信息: $response"
    exit 1
fi

echo ""
echo "======================================"
echo "清理操作完成"
echo "======================================"
