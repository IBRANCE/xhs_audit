#!/bin/bash

# 简单的启动脚本 - 仅启动Docker容器
set -e

echo "=========================================="
echo "启动Docker服务..."
echo "=========================================="

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 启动容器
echo "启动PostgreSQL和Redis..."
docker-compose up -d

echo ""
echo "✓ PostgreSQL: localhost:5432"
echo "✓ Redis: localhost:6379"
echo "✓ PgAdmin: http://localhost:5050"
echo "✓ Redis Commander: http://localhost:8081"
echo ""
echo "验证连接..."

# 等待服务启动
sleep 5

# 检查PostgreSQL
if docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then
    echo "✓ PostgreSQL 已就绪"
else
    echo "⏳ PostgreSQL 启动中，请稍候..."
    sleep 15
    docker-compose exec -T postgres pg_isready -U postgres
fi

# 检查Redis
if docker-compose exec -T redis redis-cli ping > /dev/null 2>&1; then
    echo "✓ Redis 已就绪"
else
    echo "⏳ Redis 启动中，请稍候..."
    sleep 10
    docker-compose exec -T redis redis-cli ping
fi

echo ""
echo "=========================================="
echo "✓ 所有服务已启动！"
echo "=========================================="
echo ""
echo "下一步："
echo "1. 在新终端运行: mvn spring-boot:run"
echo "2. 或运行: mvn clean compile"
echo ""
