#!/bin/bash

# 本地开发启动脚本
# 使用本地部署的AI模型进行开发测试

echo "======================================"
echo "启动 XHS Audit 本地开发环境"
echo "======================================"
echo ""
echo "配置信息:"
echo "  - 文本模型: qwen/qwen3-4b"
echo "  - 图像模型: zai-org/glm-4.6v-flash"
echo "  - 模型服务地址: http://127.0.0.1:1234/v1"
echo ""
echo "======================================"
echo ""

# 设置本地配置profile
export SPRING_PROFILES_ACTIVE=local

# 检查本地模型服务是否运行
echo "检查本地模型服务..."
if curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:1234/v1/models | grep -q "200"; then
    echo "✓ 本地模型服务运行正常"
else
    echo "✗ 警告: 本地模型服务(http://127.0.0.1:1234)未运行"
    echo "  请确保已启动模型服务后再继续"
    read -p "按回车键继续，或按 Ctrl+C 退出..."
fi

echo ""
echo "启动应用..."
echo ""

# 启动Spring Boot应用
mvn spring-boot:run -Dspring-boot.run.profiles=local
