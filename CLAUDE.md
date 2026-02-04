# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

XHS Audit (小红书内容审核系统) - An automated content moderation system for Xiaohongshu that uses LLMs via Spring AI Function Calling to audit content for violations, categories, and risk levels.

## Build & Run Commands

```bash
# Start infrastructure (PostgreSQL + Redis + Selenium Grid)
docker-compose up -d

# Build the project
mvn clean install

# Run single test class
mvn test -Dtest=TaskStatusTest

# Run specific test
mvn test -Dtest=TaskStatusTest#testIsTerminal

# Run application
mvn spring-boot:run
# or
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar
```

## Key Makefile Targets

| Command | Description |
|---------|-------------|
| `make docker-up` | Start PostgreSQL + Redis + Redis Commander |
| `make docker-down` | Stop containers |
| `make docker-selenium-up` | Start Selenium Grid (4 Chrome + 8 Firefox) |
| `make compile` | Compile project |
| `make build` | Full build with tests |
| `make test` | Run unit tests |
| `make run` | Start application |
| `make db-psql` | Connect to PostgreSQL |
| `make db-redis` | Connect to Redis CLI |

## v4.0 Architecture (Async with Redis Stream)

### Worker Pattern
The system uses a worker-based architecture for async processing:

```
┌──────────────┐     ┌─────────────────┐     ┌────────────────┐
│ API提交任务   │────▶│ Redis Stream    │────▶│ CrawlerWorker  │
│              │     │ xhs:stream:crawl│     │ (Selenium)     │
└──────────────┘     └─────────────────┘     └───────┬────────┘
                                                      │
                                                      ▼
┌──────────────┐     ┌─────────────────┐     ┌────────────────┐
│ 查询结果      │◀────│ AuditJob状态    │◀────│ AuditWorker    │
│ /api/audit   │     │ PostgreSQL      │     │ (LLM Agent)    │
└──────────────┘     └─────────────────┘     └────────────────┘
```

### Core Layers (v4.0 Updated)

| Layer | Path | Purpose |
|-------|------|---------|
| **controller/** | REST API endpoints | `AuditController`, `AsyncAuditController`, `FileUploadController` |
| **service/** | Business logic | `ContentAuditService`, `CrawlerService`, `ExcelAuditService`, `AsyncAuditService` |
| **agent/** | LLM-powered AI agent | `ContentAuditAgent` with Resilience4j circuit breaker |
| **worker/** | v4.0 Async consumers | `CrawlerWorker`, `AuditWorker` - consume from Redis Stream |
| **infrastructure/** | Resource management | `MessageQueueService`, `CrawlDuplicateFilter`, `SeleniumManager` |
| **repository/** | Spring Data JPA | Data access layer |
| **config/** | Configuration | `AsyncConfig`, `RedisStreamConstants`, `AuditPromptConfig` |
| **model/entity/** | JPA entities | `AuditJob`, `XhsContent`, `TaskStatus` enum |
| **model/dto/** | API DTOs | Records for messages and responses |

### Key Design Patterns (v4.0)

1. **Redis Stream Message Queue** (`MessageQueueService`):
   - Streams: `xhs:stream:crawl`, `xhs:stream:audit`, `xhs:stream:dead-letter`
   - Consumer groups: `crawl-workers`, `audit-workers`
   - Idempotency: `processed:crawl:{url}` keys
   - Dead letter queue for failed messages
   - Scheduled pending message recovery

2. **Worker Pattern** (CrawlerWorker, AuditWorker):
   - `@ConditionalOnProperty("audit.worker.enabled=true")`
   - Redis-based distributed locking (Redisson)
   - Graceful shutdown with `@PreDestroy`
   - Micrometer metrics for monitoring

3. **Resilience4j Circuit Breaker** (`ContentAuditAgent`):
   - Failure rate threshold: 50%
   - Slow call threshold: 80% (>30s)
   - Automatic state transitions (Open → Half-Open → Closed)
   - Retry with exponential backoff (3 attempts)

4. **Thread Pool Configuration** (`AsyncConfig`):
   - `taskExecutor`: core=10, max=50, queue=1000
   - `crawlExecutor`: core=8, max=8 (matches Selenium pool)
   - `auditTaskExecutor`: core=10, max=20 (v4.0 scaled)
   - `streamConsumerExecutor`: core=2, max=5

### Required Environment Variables

```bash
# LLM Configuration
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_CHAT_MODEL=qwen/qwen3-4b
OPENAI_VISION_MODEL=glm-4.6v-flash

# Worker Configuration (optional)
WORKER_TYPE=both        # api / crawler-worker / audit-worker / both
AUDIT_WORKER_ENABLED=true
CRAWLER_WORKER_ENABLED=true
```

## Service Endpoints

| Service | URL | Description |
|---------|-----|-------------|
| **App** | http://localhost:8080 | Main application |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | API documentation |
| **Redis Commander** | http://localhost:8081 | Redis GUI |
| **Selenium Grid** | http://localhost:4444 | Browser automation hub |

## API Endpoints (v4.0)

### Async Audit API
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/audit/async` | Submit URL for async audit |
| GET | `/api/audit/result/{jobId}` | Get audit result |
| POST | `/api/audit/async/batch` | Batch submit URLs |
| GET | `/api/audit/queue/stats` | Get queue statistics |
| GET | `/api/audit/dead-letter` | Get dead letter queue |

## Code Conventions

- **DTOs**: Java Records with `record` keyword
- **Lombok**: `@Data`, `@Builder`, `@Slf4j`
- **Exceptions**: `BusinessException`, `ResourceNotFoundException`
- **API Responses**: Wrapped in `ApiResponse<T>`
- **OpenAPI**: Controllers use `@Tag`, `@Operation` annotations
- **Logging**: Structured logging with `[module]` prefix

## Database

- **Flyway migrations**: `src/main/resources/db/migration/`
- **Key tables**: `xhs_content`, `audit_result`, `audit_job`
- **Job tracking**: `audit_job.status` (PENDING → PROCESSING → COMPLETED)

## Testing Notes

- Unit tests: `src/test/java/**/*Test.java`
- Integration tests require Docker services running
- Some tests use H2 in-memory database
- Avoid running all tests at once (memory intensive)
