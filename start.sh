#!/bin/bash

# 小红书审核系统 - 快速启动脚本
# 版本: 1.0.0

set -e

echo "======================================"
echo "  小红书审核系统 - 启动脚本"
echo "======================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 1. 检查Java环境
log_info "检查Java环境..."
if ! command -v java &> /dev/null; then
    log_error "未找到Java,请先安装JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    log_error "Java版本过低,需要JDK 17+,当前版本: $JAVA_VERSION"
    exit 1
fi
log_info "✓ Java版本检查通过"

# 2. 检查PostgreSQL
log_info "检查PostgreSQL..."
if ! command -v psql &> /dev/null; then
    log_error "未找到PostgreSQL,请先安装PostgreSQL"
    exit 1
fi

# 测试数据库连接
if psql -U postgres -d xhs_audit -c "SELECT 1;" &> /dev/null; then
    log_info "✓ PostgreSQL连接正常"
else
    log_error "无法连接到PostgreSQL数据库 xhs_audit"
    log_info "请确保PostgreSQL已启动,且数据库xhs_audit已创建"
    exit 1
fi

# 3. 检查Redis
log_info "检查Redis..."
if ! command -v redis-cli &> /dev/null; then
    log_error "未找到Redis,请先安装Redis"
    exit 1
fi

if redis-cli ping &> /dev/null; then
    log_info "✓ Redis连接正常"
else
    log_warn "Redis未运行,尝试启动..."
    if command -v brew &> /dev/null; then
        brew services start redis
        sleep 2
        if redis-cli ping &> /dev/null; then
            log_info "✓ Redis启动成功"
        else
            log_error "Redis启动失败"
            exit 1
        fi
    else
        log_error "请手动启动Redis服务"
        exit 1
    fi
fi

# 4. 检查Playwright浏览器
log_info "检查Playwright..."
if [ ! -d "$HOME/.cache/ms-playwright" ]; then
    log_warn "Playwright浏览器未安装,将自动安装..."
    mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
fi
log_info "✓ Playwright检查完成"

# 5. 停止旧进程
log_info "检查是否有旧进程..."
OLD_PID=$(lsof -ti:8080 || true)
if [ -n "$OLD_PID" ]; then
    log_warn "端口8080被占用,正在停止旧进程 PID: $OLD_PID"
    kill $OLD_PID
    sleep 2
fi

# 6. 启动应用
log_info "正在启动应用..."
LOG_FILE="/tmp/xhs_audit_$(date +%Y%m%d_%H%M%S).log"

# 后台启动Maven
nohup mvn spring-boot:run > "$LOG_FILE" 2>&1 &
APP_PID=$!

echo ""
log_info "应用正在启动,PID: $APP_PID"
log_info "日志文件: $LOG_FILE"

# 等待应用启动
log_info "等待应用启动完成..."
MAX_WAIT=60
WAITED=0

while [ $WAITED -lt $MAX_WAIT ]; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        break
    fi
    echo -n "."
    sleep 2
    WAITED=$((WAITED + 2))
done

echo ""

# 7. 检查应用状态
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    log_info "✓ 应用启动成功!"
    echo ""
    echo "======================================"
    echo "  应用信息"
    echo "======================================"
    echo "  应用名称: 小红书审核系统"
    echo "  访问地址: http://localhost:8080"
    echo "  健康检查: http://localhost:8080/actuator/health"
    echo "  进程ID:   $APP_PID"
    echo "  日志文件: $LOG_FILE"
    echo ""
    echo "======================================"
    echo "  快速测试命令"
    echo "======================================"
    echo "  # 健康检查"
    echo "  curl http://localhost:8080/actuator/health | jq '.'"
    echo ""
    echo "  # 查看日志"
    echo "  tail -f $LOG_FILE"
    echo ""
    echo "  # 停止应用"
    echo "  kill $APP_PID"
    echo ""
    echo "  # API测试手册"
    echo "  cat test-api.md"
    echo "======================================"
    
    # 显示健康状态
    echo ""
    log_info "当前健康状态:"
    curl -s http://localhost:8080/actuator/health | jq '.'
    
else
    log_error "应用启动失败,请查看日志: $LOG_FILE"
    log_info "最近的错误日志:"
    tail -50 "$LOG_FILE" | grep -i error || tail -50 "$LOG_FILE"
    exit 1
fi

echo ""
log_info "启动完成! 可以开始测试了 🎉"
