.PHONY: help setup docker-up docker-down docker-logs docker-selenium-up docker-selenium-down clean build compile test test-watch run debug logs check-env health stream-stats stream-pending selenium-status dead-letter

# 颜色定义
BLUE=\033[0;34m
GREEN=\033[0;32m
YELLOW=\033[1;33m
NC=\033[0m

# 项目配置
PROJECT_NAME=xhs-audit
JAVA_OPTS=-Xmx1024m -Xms512m

help: ## 显示帮助信息
	@echo "$(BLUE)XHS Audit - Makefile 帮助$(NC)"
	@echo ""
	@echo "$(YELLOW)环境管理:$(NC)"
	@echo "  make check-env        - 检查环境和依赖"
	@echo "  make setup            - 完整的一次性设置"
	@echo ""
	@echo "$(YELLOW)Docker 管理:$(NC)"
	@echo "  make docker-up        - 启动 Docker 容器"
	@echo "  make docker-down      - 停止 Docker 容器"
	@echo "  make docker-logs      - 查看 Docker 日志"
	@echo "  make docker-selenium-up   - 启动 Selenium Grid"
	@echo "  make docker-selenium-down - 停止 Selenium Grid"
	@echo ""
	@echo "$(YELLOW)构建和编译:$(NC)"
	@echo "  make clean            - 清理构建文件"
	@echo "  make compile          - 编译项目"
	@echo "  make build            - 完整构建 (clean + compile + package)"
	@echo "  make build-skip-test  - 跳过测试的快速构建"
	@echo ""
	@echo "$(YELLOW)测试:$(NC)"
	@echo "  make test             - 运行所有单元测试"
	@echo "  make test-coverage    - 生成覆盖率报告"
	@echo "  make test-specific    - 运行特定测试 (TEST=ClassName)"
	@echo "  make dev-test         - 启动本地 dev 环境测试 (start-local.sh)"
	@echo ""
	@echo "$(YELLOW)运行应用:$(NC)"
	@echo "  make run              - 运行应用"
	@echo "  make debug            - 以调试模式运行"
	@echo ""
	@echo "$(YELLOW)数据库:$(NC)"
	@echo "  make db-psql          - 连接 PostgreSQL"
	@echo "  make db-redis         - 连接 Redis"
	@echo "  make db-flush         - 清空所有数据库"
	@echo ""
	@echo "$(YELLOW)v4.0 Stream 监控:$(NC)"
	@echo "  make health           - 综合健康检查"
	@echo "  make stream-stats     - Redis Stream 统计"
	@echo "  make stream-pending   - Pending 消息统计"
	@echo "  make dead-letter     - 查看死信队列"
	@echo "  make selenium-status  - Selenium Grid 状态"
	@echo ""
	@echo "$(YELLOW)日志和监控:$(NC)"
	@echo "  make logs             - 查看应用日志"
	@echo "  make logs-docker      - 查看 Docker 日志"

check-env: ## 检查环境和依赖
	@echo "$(BLUE)检查环境...$(NC)"
	@./scripts/check-env.sh

setup: ## 完整的一次性设置
	@echo "$(BLUE)设置开发环境...$(NC)"
	@make check-env
	@echo ""
	@echo "$(YELLOW)启动 Docker...$(NC)"
	@make docker-up
	@echo ""
	@echo "$(YELLOW)编译项目...$(NC)"
	@make compile
	@echo ""
	@echo "$(GREEN)✓ 设置完成！$(NC)"
	@echo "运行 'make run' 启动应用"

# Docker 管理
docker-up: ## 启动 PostgreSQL 和 Redis 容器
	@echo "$(BLUE)启动 Docker 容器...$(NC)"
	@docker compose up -d
	@sleep 5
	@echo "$(GREEN)✓ 容器已启动$(NC)"
	@echo "  PostgreSQL: localhost:5432"
	@echo "  Redis: localhost:6379"
	@echo "  PgAdmin: http://localhost:5050"
	@echo "  Redis Commander: http://localhost:8081"

docker-down: ## 停止 Docker 容器
	@echo "$(BLUE)停止 Docker 容器...$(NC)"
	@docker compose down
	@echo "$(GREEN)✓ 容器已停止$(NC)"

docker-logs: ## 查看 Docker 日志
	@docker compose logs -f

docker-selenium-up: ## 启动 Selenium Grid (4 Chrome + 8 Firefox)
	@echo "$(BLUE)启动 Selenium Grid...$(NC)"
	@docker compose -f docker-compose-selenium.yml up -d
	@sleep 10
	@echo "$(GREEN)✓ Selenium Grid 已启动$(NC)"
	@echo "  Hub: http://localhost:4444"
	@echo "  节点: 8 Firefox"

docker-selenium-down: ## 停止 Selenium Grid
	@echo "$(BLUE)停止 Selenium Grid...$(NC)"
	@docker compose -f docker-compose-selenium.yml down
	@echo "$(GREEN)✓ Selenium Grid 已停止$(NC)"

# 构建和编译
clean: ## 清理构建文件
	@echo "$(BLUE)清理构建文件...$(NC)"
	@mvn clean
	@rm -rf target/
	@echo "$(GREEN)✓ 清理完成$(NC)"

compile: ## 编译项目
	@echo "$(BLUE)编译项目...$(NC)"
	@mvn clean compile
	@echo "$(GREEN)✓ 编译成功$(NC)"

build: ## 完整构建
	@echo "$(BLUE)完整构建...$(NC)"
	@mvn clean install
	@echo "$(GREEN)✓ 构建成功$(NC)"

build-skip-test: ## 跳过测试的快速构建
	@echo "$(BLUE)快速构建 (跳过测试)...$(NC)"
	@mvn clean package -DskipTests
	@echo "$(GREEN)✓ 构建成功$(NC)"

# 测试
test: ## 运行所有单元测试
	@echo "$(BLUE)运行测试...$(NC)"
	@mvn test
	@echo "$(GREEN)✓ 测试完成$(NC)"

test-coverage: ## 生成覆盖率报告
	@echo "$(BLUE)生成覆盖率报告...$(NC)"
	@mvn clean test jacoco:report
	@open target/site/jacoco/index.html 2>/dev/null || echo "报告已生成: target/site/jacoco/index.html"
	@echo "$(GREEN)✓ 报告已生成$(NC)"

test-specific: ## 运行特定测试 (TEST=ClassName)
	@if [ -z "$(TEST)" ]; then \
		echo "$(YELLOW)用法: make test-specific TEST=PlaywrightManagerTest$(NC)"; \
		exit 1; \
	fi
	@echo "$(BLUE)运行测试: $(TEST)...$(NC)"
	@mvn test -Dtest=$(TEST)
	@echo "$(GREEN)✓ 完成$(NC)"

dev-test: ## 运行本地 dev 环境测试 (start-local.sh)
	@echo "$(BLUE)启动本地 dev 环境测试...$(NC)"
	@./start-local.sh
	@echo "$(GREEN)✓ dev 环境测试完成$(NC)"

# 运行应用
run: ## 运行应用
	@echo "$(BLUE)启动应用...$(NC)"
	@JAVA_OPTS="$(JAVA_OPTS)" mvn spring-boot:run

debug: ## 以调试模式运行
	@echo "$(BLUE)以调试模式启动应用...$(NC)"
	@JAVA_OPTS="$(JAVA_OPTS)" mvn spring-boot:run -Dspring-boot.run.arguments="--debug"

# 数据库管理
db-psql: ## 连接 PostgreSQL
	@echo "$(BLUE)连接 PostgreSQL...$(NC)"
	@docker compose exec postgres psql -U postgres -d xhs_audit

db-redis: ## 连接 Redis
	@echo "$(BLUE)连接 Redis...$(NC)"
	@docker compose exec redis redis-cli

db-flush: ## 清空所有数据库
	@echo "$(YELLOW)⚠ 警告: 这将清空所有数据库数据 (PostgreSQL + Redis)!$(NC)"
	@read -p "确认? (yes/no) " confirm; \
	if [ "$$confirm" = "yes" ]; then \
		echo "清空 PostgreSQL 表数据..."; \
		docker compose exec postgres psql -U postgres -d xhs_audit -c "\
			TRUNCATE TABLE xhs_content, audit_result, audit_rule, sensitive_word, audit_job \
			RESTART IDENTITY CASCADE;"; \
		echo "清空 Redis..."; \
		docker compose exec redis redis-cli FLUSHALL; \
		echo "$(GREEN)✓ 完成$(NC)"; \
	fi

# 日志
logs: ## 查看应用日志
	@tail -f target/spring.log 2>/dev/null || echo "日志文件不存在。应用可能未运行。"

logs-docker: ## 查看 Docker 日志
	@docker compose logs -f

# v4.0 Stream 监控
health: ## 综合健康检查
	@echo "$(BLUE)=== 健康检查 ===$(NC)"
	@echo -n "Application: "; curl -s http://localhost:8080/actuator/health | jq -r '.status' 2>/dev/null || echo "N/A"
	@echo -n "PostgreSQL: "; docker compose exec postgres pg_isready -U postgres -q && echo "UP" || echo "DOWN"
	@echo -n "Redis: "; docker compose exec redis redis-cli ping -q && echo "UP" || echo "DOWN"

stream-stats: ## Redis Stream 统计
	@echo "$(BLUE)=== Stream 统计 ===$(NC)"
	@echo "Crawl Stream:   $$(docker compose exec redis redis-cli XLEN xhs:stream:crawl 2>/dev/null || echo 0) 条"
	@echo "Audit Stream:   $$(docker compose exec redis redis-cli XLEN xhs:stream:audit 2>/dev/null || echo 0) 条"
	@echo "Dead Letter:    $$(docker compose exec redis redis-cli XLEN xhs:stream:dead-letter 2>/dev/null || echo 0) 条"

stream-pending: ## Pending 消息统计
	@echo "$(BLUE)=== Pending 消息 ===$(NC)"
	@echo "Crawl Pending:"; docker compose exec redis redis-cli XPENDING xhs:stream:crawl crawl-workers 2>/dev/null || echo "  N/A"
	@echo "Audit Pending:"; docker compose exec redis redis-cli XPENDING xhs:stream:audit audit-workers 2>/dev/null || echo "  N/A"

dead-letter: ## 查看死信队列
	@echo "$(BLUE)=== Dead Letter Queue ===$(NC)"
	@docker compose exec redis redis-cli XRANGE xhs:stream:dead-letter - + COUNT 5 2>/dev/null || echo "空或错误"

selenium-status: ## Selenium Grid 状态
	@echo "$(BLUE)=== Selenium Grid 状态 ===$(NC)"
	@curl -s http://localhost:4444/status 2>/dev/null | jq -c '.ready, {nodes: (.nodes | length)}' 2>/dev/null || echo "Selenium Grid 未运行"

# 快捷组合命令
fresh-start: clean docker-down setup ## 完整的重新开始
	@echo "$(GREEN)✓ 全新开始完成！$(NC)"

full-test: compile test test-coverage ## 完整的测试流程
	@echo "$(GREEN)✓ 测试完成！$(NC)"

dev: docker-up compile ## 启动开发环境
	@echo "$(GREEN)✓ 开发环境就绪！$(NC)"
	@echo "运行 'make run' 启动应用"

# 默认目标
.DEFAULT_GOAL := help
