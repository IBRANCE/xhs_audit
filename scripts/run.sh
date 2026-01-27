#!/bin/bash

# 编译并运行项目
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "编译并运行项目"
echo "=========================================="

# 检查Docker服务
echo "检查Docker服务..."
if ! docker-compose ps | grep -q postgres; then
    echo "启动Docker容器..."
    docker-compose up -d
    sleep 10
fi

echo "编译项目..."
mvn clean compile

echo "启动应用..."
mvn spring-boot:run
