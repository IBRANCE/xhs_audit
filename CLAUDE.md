# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

XHS Audit (小红书内容审核系统) - An automated content moderation system for Xiaohongshu that uses LLMs via Spring AI Function Calling to audit content for violations, categories, and risk levels.

## Build & Run Commands

```bash
# Start infrastructure (PostgreSQL + Redis)
docker-compose up -d

# Build the project
mvn clean install

# Run tests
mvn test

# Run application
mvn spring-boot:run
# or
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar
```

## Key Makefile Targets

| Command | Description |
|---------|-------------|
| `make docker-up` | Start PostgreSQL + Redis |
| `make docker-down` | Stop containers |
| `make compile` | Compile project |
| `make build` | Full build with tests |
| `make test` | Run unit tests |
| `make run` | Start application |
| `make db-psql` | Connect to PostgreSQL |
| `make db-redis` | Connect to Redis CLI |

## Architecture

### Core Layers
- **controller/** - REST API endpoints (AuditController, FileUploadController)
- **service/** - Business logic (ContentAuditService, CrawlerService, ExcelAuditService)
- **agent/** - LLM-powered AI agent (ContentAuditAgent)
- **function/** - Function Calling tools for the agent
- **infrastructure/** - Resource management (PlaywrightManager for browser pooling)
- **repository/** - Spring Data JPA data access
- **model/entity/** - JPA entities
- **model/dto/** - API request/response DTOs

### Key Design Patterns

1. **Browser Pool** (`PlaywrightManager`): Uses `BlockingQueue<BrowserInstance>` for concurrent access; `PageWrapper` implements `AutoCloseable` for try-with-resources cleanup.

2. **LLM Agent** (`ContentAuditAgent`): Dual ChatClient setup - `textChatClient` for text and `visionChatClient` for images. Uses Function Calling with `AuditRuleFunctions`.

3. **Three-Level Caching**: L1 Caffeine (local, 10min TTL), L2 Redis (distributed, 24hr), L3 PostgreSQL.

4. **Async Processing**: Configurable `ThreadPoolTaskExecutor` with `@EnableAsync`.

### Required Environment Variables
```
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_CHAT_MODEL=qwen/qwen3-4b
OPENAI_VISION_MODEL=glm-4.6v-flash
```

## Service Endpoints
- **App**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Redis Commander**: http://localhost:8081

## Code Conventions

- DTOs use Java Records
- Lombok annotations: `@Data`, `@Builder`, `@Slf4j`
- Custom exceptions: `BusinessException`, `ResourceNotFoundException`
- API responses wrapped in `ApiResponse<T>`
- Controllers use OpenAPI annotations (`@Tag`, `@Operation`)

## Database

- Flyway migrations for schema management
- Key tables: `xhs_content`, `audit_result`, `audit_job`, `audit_rule`, `sensitive_word`
