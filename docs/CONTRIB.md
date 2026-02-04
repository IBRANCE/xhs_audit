# Contributing Guide

This guide provides comprehensive instructions for developers working on the XHS Audit project.

## Project Overview

XHS Audit (小红书内容审核系统) is an automated content moderation system for Xiaohongshu that uses LLMs via Spring AI Function Calling to audit content for violations, categories, and risk levels.

**Tech Stack:**
- Java 21 + Spring Boot 3.3.5
- Maven build system
- PostgreSQL + Redis (with Redis Stream for async processing)
- Selenium Grid for browser automation
- Spring AI for LLM integration

---

## Development Environment Setup

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21+ | Runtime environment |
| Maven | 3.8+ | Build tool |
| Docker | 20.x+ | Container runtime |
| Docker Compose | 2.x+ | Container orchestration |

### Quick Start

```bash
# 1. Clone the repository
git clone <repository-url>
cd xhs_audit

# 2. Start infrastructure services
make docker-up

# 3. Configure environment
cp .env.example .env
# Edit .env with your API keys

# 4. Build the project
mvn clean install

# 5. Run the application
mvn spring-boot:run
```

---

## Docker Services

The project requires the following Docker services for development:

### Core Services

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Primary database |
| Redis | 6379 | Cache and message queue |
| Redis Commander | 8081 | Redis GUI management |
| PgAdmin | 5050 | PostgreSQL GUI (optional) |

### Selenium Grid (for Crawling)

| Service | Port | Description |
|---------|------|-------------|
| Selenium Hub | 4444 | Browser automation hub |
| Chrome Nodes | 7900+ | Chrome browser instances |
| Firefox Nodes | 5800+ | Firefox browser instances |

### Starting Services

```bash
# Start all services
docker-compose up -d

# Start Selenium Grid (extended browser support)
make docker-selenium-up

# Stop all services
make docker-down

# View logs
docker-compose logs -f
```

### Verify Services

```bash
# Check PostgreSQL
docker exec xhs-audit-postgres pg_isready -U postgres

# Check Redis
docker exec xhs-audit-redis redis-cli ping

# Check Selenium Grid
curl http://localhost:4444/status
```

---

## Maven Commands

### Build Commands

```bash
# Clean previous build artifacts
mvn clean

# Compile the project (no test execution)
mvn clean compile

# Full build with tests
mvn clean install

# Quick build (skip tests)
mvn clean package -DskipTests

# Build with JaCoCo coverage report
mvn clean test jacoco:report
```

### Test Commands

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=TaskStatusTest

# Run specific test method
mvn test -Dtest=TaskStatusTest#testIsTerminal

# Run with coverage report
mvn clean test jacoco:report

# Run tests in specific package
mvn test -Dtest="com.xhs.audit.service.*"
```

### Running the Application

```bash
# Run with Maven
mvn spring-boot:run

# Run with custom JVM options
JAVA_OPTS="-Xmx1024m -Xms512m" mvn spring-boot:run

# Run packaged JAR
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Environment Configuration

### Required Environment Variables

Create a `.env` file from the template:

```bash
cp .env.example .env
```

### LLM Configuration

```bash
# OpenAI API (required)
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_CHAT_MODEL=qwen/qwen3-4b
OPENAI_VISION_MODEL=glm-4.6v-flash
```

### Database Configuration

```bash
# PostgreSQL (defaults match docker-compose)
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=xhs_audit
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

### Redis Configuration

```bash
# Redis (defaults match docker-compose)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

### Worker Configuration

```bash
# Worker mode: api / crawler-worker / audit-worker / both
WORKER_TYPE=both

# Enable/disable specific workers
AUDIT_WORKER_ENABLED=true
CRAWLER_WORKER_ENABLED=true
```

---

## Makefile Commands Reference

| Command | Description |
|---------|-------------|
| `make help` | Show all available commands |
| `make setup` | Complete one-time environment setup |
| `make docker-up` | Start PostgreSQL + Redis |
| `make docker-down` | Stop Docker containers |
| `make compile` | Compile the project |
| `make build` | Full build with tests |
| `make build-skip-test` | Quick build without tests |
| `make test` | Run all unit tests |
| `make test-coverage` | Generate coverage report |
| `make test-specific TEST=ClassName` | Run specific test |
| `make run` | Start the application |
| `make debug` | Start in debug mode |
| `make db-psql` | Connect to PostgreSQL CLI |
| `make db-redis` | Connect to Redis CLI |
| `make logs` | View application logs |
| `make dev` | Start development environment |

---

## Project Structure

```
xhs_audit/
├── src/main/java/com/xhs/audit/
│   ├── config/           # Configuration classes
│   ├── controller/       # REST API endpoints
│   ├── service/          # Business logic
│   ├── agent/           # LLM-powered AI agents
│   ├── worker/          # Async message consumers
│   ├── infrastructure/  # Resource management
│   ├── repository/      # Data access layer
│   ├── model/
│   │   ├── entity/     # JPA entities
│   │   └── dto/        # Data transfer objects
│   └── exception/      # Custom exceptions
├── src/main/resources/
│   ├── db/migration/   # Flyway migrations
│   ├── application*.yml # Spring configuration
│   └── static/         # Static assets (UI)
├── docs/               # Documentation
├── scripts/            # Utility scripts
└── docker-compose.yml  # Docker services
```

---

## Code Conventions

### Java Standards

- **DTOs**: Use Java Records (`record` keyword)
- **Lombok**: `@Data`, `@Builder`, `@Slf4j`
- **Exceptions**: `BusinessException`, `ResourceNotFoundException`
- **API Responses**: Wrap in `ApiResponse<T>`
- **OpenAPI**: Use `@Tag`, `@Operation` annotations

### Logging

Use structured logging with module prefixes:

```java
@Slf4j
public class CrawlerService {
    public void process() {
        log.info("[Crawler] Starting crawl for URL: {}", url);
        // ...
    }
}
```

### API Documentation

All REST endpoints must include OpenAPI annotations:

```java
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit API", description = "Content audit endpoints")
public class AsyncAuditController {
    @PostMapping("/async")
    @Operation(summary = "Submit async audit")
    public ApiResponse<AuditResult> submitAsync(@RequestBody AuditRequest request) {
        // ...
    }
}
```

---

## Database Management

### Flyway Migrations

Migrations are located in `src/main/resources/db/migration/`:

```bash
# Run migrations (automatic on startup)
mvn spring-boot:run

# Check migration status
docker exec xhs-audit-postgres psql -U postgres -d xhs_audit \
  -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

### Key Tables

| Table | Purpose |
|-------|---------|
| `audit_job` | Job tracking and status |
| `xhs_content` | Crawled content data |
| `audit_result` | Audit results |

### Database Commands

```bash
# Connect to PostgreSQL
make db-psql

# Connect to Redis
make db-redis

# Flush Redis cache
make db-flush
```

---

## Testing

### Unit Tests

- Location: `src/test/java/**/*Test.java`
- Framework: JUnit 5 + Mockito
- Test database: H2 in-memory

### Integration Tests

- Require Docker services running
- Use Testcontainers for PostgreSQL
- Located in `src/test/java/**/*IT.java`

### Running Tests

```bash
# All tests
mvn test

# Specific test
mvn test -Dtest=TaskStatusTest

# With coverage
mvn clean test jacoco:report
```

---

## Development Workflow

### 1. Create Feature Branch

```bash
git checkout -b feature/your-feature-name
```

### 2. Make Changes

Follow code conventions and add tests.

### 3. Run Tests

```bash
mvn clean test
```

### 4. Commit Changes

```bash
git add .
git commit -m "feat: add new feature"
```

### 5. Submit Pull Request

Push to remote and create PR for review.

---

## Troubleshooting

### Redis Connection Failed

```bash
# Check Redis is running
docker ps | grep redis

# Check Redis logs
docker logs xhs-audit-redis

# Verify connection
docker exec xhs-audit-redis redis-cli ping
```

### PostgreSQL Connection Issues

```bash
# Check PostgreSQL status
docker ps | grep postgres

# Check logs
docker logs xhs-audit-postgres

# Verify database exists
docker exec xhs-audit-postgres psql -U postgres -l
```

### Maven Build Fails

```bash
# Clean and retry
mvn clean
mvn clean install -U  # Force update dependencies

# Check Java version
java -version
```

---

## Additional Resources

- API Documentation: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Redis Commander: [http://localhost:8081](http://localhost:8081)
- Selenium Grid: [http://localhost:4444](http://localhost:4444)
- Health Check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
