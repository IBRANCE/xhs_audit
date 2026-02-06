# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with this repository.

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

# Run specific test method
mvn test -Dtest=TaskStatusTest#testIsTerminal

# Run application
mvn spring-boot:run
# or
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar
```

## Key Makefile Targets

| Command | Description |
|---------|-------------|
| `make docker-up` | Start PostgreSQL + Redis + Redis Commander + PgAdmin |
| `make docker-down` | Stop containers |
| `make docker-selenium-up` | Start Selenium Grid (4 Chrome + 8 Firefox nodes) |
| `make compile` | Clean and compile project |
| `make build` | Full build with tests |
| `make build-skip-test` | Quick build without tests |
| `make test` | Run all unit tests |
| `make test-specific TEST=ClassName` | Run specific test class |
| `make test-coverage` | Generate JaCoCo coverage report |
| `make run` | Start application |
| `make db-psql` | Connect to PostgreSQL CLI |
| `make db-redis` | Connect to Redis CLI |
| `make dev` | Docker up + compile in one command |

## v4.0 Architecture (Async with Redis Stream)

### Worker Pattern
The system uses a worker-based architecture for async processing:

```
┌──────────────┐     ┌─────────────────┐     ┌────────────────┐
│ API提交任务   │────▶│ Redis Stream    │────▶│ CrawlerWorker  │
│ /api/audit   │     │ xhs:stream:crawl│     │ (Selenium)     │
└──────────────┘     └─────────────────┘     └───────┬────────┘
                                                      │
                                                      ▼
                                              ┌────────────────┐
                                              │ PostgreSQL     │
                                              │ (save content) │
                                              └───────┬────────┘
                                                      │
                                                      ▼
                                              ┌────────────────┐
                                              │ Redis Stream   │
                                              │ xhs:stream:audit│
                                              └───────┬────────┘
                                                      │
                                                      ▼
                                              ┌────────────────┐
                                              │ AuditWorker    │
                                              │ (LLM Agent)    │
                                              └───────┬────────┘
                                                      │
                                                      ▼
                                              ┌────────────────┐
                                              │ Query Result   │
                                              │ /api/audit/... │
                                              └────────────────┘
```

### Core Layers (v4.0)

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
| **exception/** | Exception handling | `BusinessException`, `GlobalExceptionHandler` |

### Key Design Patterns (v4.0)

1. **Redis Stream Message Queue** (`MessageQueueService`):
   - Streams: `xhs:stream:crawl`, `xhs:stream:audit`, `xhs:stream:dead-letter`
   - Consumer groups: `crawl-workers`, `audit-workers`
   - Idempotency: `processed:crawl:{jobId}:{url}` keys (24h TTL)
   - Dead letter queue for failed messages (max 3 retries)
   - Scheduled pending message recovery (every 60s, 5min idle threshold)

2. **Worker Pattern** (`CrawlerWorker`, `AuditWorker`):
   - `@ConditionalOnProperty("audit.worker.enabled=true")`
   - Multiple consumer threads per worker (configurable via `audit.crawler.concurrent-crawl-threads`)
   - Redis-based distributed locking (Redisson) - URL-level locks
   - Graceful shutdown with `@PreDestroy` annotation
   - Micrometer metrics for monitoring

3. **Resilience4j Circuit Breaker** (`ContentAuditAgent`):
   - Failure rate threshold: 50%
   - Slow call threshold: 80% (>30s)
   - Automatic state transitions: Open → Half-Open → Closed
   - Retry with exponential backoff: 3 attempts, 2s base delay

4. **Thread Pool Configuration** (`AsyncConfig`):
   - `taskExecutor`: core=10, max=50, queue=1000
   - `crawlExecutor`: core=8, max=8 (matches Selenium pool)
   - `auditTaskExecutor`: core=10, max=20 (v4.0 scaled)
   - `streamConsumerExecutor`: core=2, max=5

5. **Selenium Grid Integration** (`SeleniumManager`):
   - Pool size: 8 drivers (4 Chrome + 8 Firefox via Grid)
   - Mobile device emulation enabled by default
   - Health checks and automatic recovery

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

# Selenium Grid (optional)
SELENIUM_GRID_URL=http://localhost:4444
SELENIUM_POOL_SIZE=8
```

## Service Endpoints

| Service | URL | Description |
|---------|-----|-------------|
| **App** | http://localhost:8080 | Main application |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | API documentation |
| **Redis Commander** | http://localhost:8081 | Redis GUI |
| **Selenium Grid** | http://localhost:4444 | Browser automation hub |
| **PgAdmin** | http://localhost:5050 | PostgreSQL admin (admin/admin) |

## API Endpoints (v4.0)

### Async Audit API
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/audit/async` | Submit URL for async audit |
| GET | `/api/audit/result/{jobId}` | Get audit result |
| POST | `/api/audit/async/batch` | Batch submit URLs via Excel |
| GET | `/api/audit/queue/stats` | Get queue statistics |
| GET | `/api/audit/dead-letter` | Get dead letter queue |

### Health & Admin
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/actuator/metrics` | Metrics endpoint |
| GET | `/actuator/prometheus` | Prometheus metrics |

## Code Conventions

- **DTOs**: Java Records with `record` keyword (e.g., `record AuditTaskMessage(...)`)
- **Lombok**: `@Data`, `@Builder`, `@Slf4j` for POJOs
- **Exceptions**: `BusinessException(code, message)`, `ResourceNotFoundException`
- **API Responses**: Wrapped in `ApiResponse<T>` with factory methods:
  - `ApiResponse.success(data)`
  - `ApiResponse.error(code, message)`
- **OpenAPI**: Controllers use `@Tag`, `@Operation` annotations
- **Logging**: Structured logging with `[module]` prefix (e.g., `[CrawlerWorker]`)

### Response Format
```json
{
  "code": "000000",
  "message": "success",
  "data": { ... },
  "timestamp": "2026-02-06T10:00:00"
}
```

## Database

- **Flyway migrations**: `src/main/resources/db/migration/`
- **Key tables**: `audit_job`, `xhs_content`, `audit_result`, `audit_rule`, `sensitive_word`
- **Job tracking**: `audit_job.status` (PENDING → CRAWLING → CRAWLED → AUDITING → COMPLETED/FAILED)
- **Status enum**: `TaskStatus` with `isTerminal()`, `isSuccess()`, `isAllowedTransition()` methods

## Testing Notes

- **Unit tests**: `src/test/java/**/*Test.java` using JUnit 5
- **Integration tests**: Require Docker services running (PostgreSQL, Redis)
- **H2 in-memory database**: Used for tests without Docker
- **Testcontainers**: For integration tests with real PostgreSQL
- **E2E tests**: `src/test/java/**/e2e/*Test.java` for end-to-end workflows
- **Avoid**: Running all tests at once (memory intensive with Selenium)

### Test Examples
```bash
# Run single test class
mvn test -Dtest=TaskStatusTest

# Run specific test method
mvn test -Dtest=TaskStatusTest#testIsTerminal

# Run with coverage
mvn test jacoco:report
```

## Redis Stream Constants

```java
// Stream names
xhs:stream:crawl        // Crawler task queue
xhs:stream:audit         // Audit task queue
xhs:stream:dead-letter  // Dead letter queue

// Consumer groups
crawl-workers           // Crawler consumer group
audit-workers           // Audit consumer group

// Key prefixes
processed:crawl:{jobId}:{url}  // Deduplication (24h TTL)
crawler:processing:{hash}      // Distributed lock
audit:processing:{hash}       // Distributed lock
```

## Key Configuration Files

| File | Purpose |
|------|---------|
| `application.yml` | Main Spring Boot configuration |
| `pom.xml` | Maven dependencies (Spring Boot 3.3.5, Spring AI 1.1.0-M5) |
| `docker-compose.yml` | PostgreSQL, Redis, Selenium Grid services |
| `Makefile` | Development convenience commands |
