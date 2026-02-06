# Operations Runbook

This runbook provides procedures for deploying, monitoring, and troubleshooting the XHS Audit system.

---

## Deployment Procedures

### Development Deployment

```bash
# 1. Start infrastructure
make docker-up

# 2. Wait for services to be ready
sleep 10

# 3. Build the application
mvn clean package -DskipTests

# 4. Run the application
mvn spring-boot:run
# or
java -jar target/xhs-audit-1.0.0-SNAPSHOT.jar
```

### Production Deployment

```bash
# 1. Build Docker image
docker build -t xhs-audit:latest .

# 2. Deploy with docker-compose
docker-compose -f docker-compose-production.yml up -d

# 3. Verify deployment
curl http://localhost:8080/actuator/health
```

### Rolling Update

```bash
# Pull latest image
docker pull xhs-audit:latest

# Restart services one by one
docker-compose -f docker-compose-production.yml restart api
docker-compose -f docker-compose-production.yml restart crawler-worker-1
docker-compose -f docker-compose-production.yml restart crawler-worker-2
docker-compose -f docker-compose-production.yml restart audit-worker-1
docker-compose -f docker-compose-production.yml restart audit-worker-2
```

### v4.0 Async Worker Deployment

The v4.0 architecture supports distributed worker deployment:

```bash
# Deploy API server only
docker-compose up -d api

# Deploy crawler worker only
docker-compose up -d crawler-worker

# Deploy audit worker only
docker-compose up -d audit-worker

# Deploy all workers
WORKER_TYPE=both docker-compose up -d
```

---

## Monitoring

### Service Health Checks

| Service | URL | Expected Response |
|---------|-----|------------------|
| Application | `http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| PostgreSQL | `docker exec xhs-audit-postgres pg_isready` | `accepting connections` |
| Redis | `docker exec xhs-audit-redis redis-cli ping` | `PONG` |
| Selenium Grid | `http://localhost:4444/status` | JSON status |

### Health Check Commands

```bash
# Check all services
docker-compose ps

# Application health
curl -s http://localhost:8080/actuator/health | jq

# Prometheus metrics
curl -s http://localhost:8080/actuator/prometheus

# Database connections
docker exec xhs-audit-postgres psql -U postgres -d xhs_audit \
  -c "SELECT count(*) FROM pg_stat_activity;"
```

### Selenium Grid Console

Access the Selenium Grid dashboard at: **http://localhost:4444**

Check:
- Number of active sessions
- Node availability (4 Chrome + 8 Firefox)
- Browser versions

### Redis Commander

Access Redis GUI at: **http://localhost:8081**

Monitor:
- Stream message counts (`xhs:stream:crawl`, `xhs:stream:audit`)
- Key space usage
- Consumer group status (`crawl-workers`, `audit-workers`)

### Log Monitoring

```bash
# Application logs
tail -f target/spring.log

# Docker container logs
docker-compose logs -f

# Specific service logs
docker logs xhs-audit-api --tail 100
docker logs xhs-audit-crawler-1 --tail 100
docker logs xhs-audit-audit-1 --tail 100
```

---

## v4.0 Redis Stream Monitoring

### Stream Statistics

```bash
# Check stream length
docker exec xhs-audit-redis redis-cli XLEN xhs:stream:crawl
docker exec xhs-audit-redis redis-cli XLEN xhs:stream:audit

# Check pending messages
docker exec xhs-audit-redis redis-cli XPENDING xhs:stream:crawl
docker exec xhs-audit-redis redis-cli XPENDING xhs:stream:audit

# Check consumer groups
docker exec xhs-audit-redis redis-cli XINFO GROUPS xhs:stream:crawl
docker exec xhs-audit-redis redis-cli XINFO GROUPS xhs:stream:audit
```

### Dead Letter Queue Monitoring

```bash
# Check dead letter queue
docker exec xhs-audit-redis redis-cli XLEN xhs:stream:dead-letter

# View dead letter messages
docker exec xhs-audit-redis redis-cli XRANGE xhs:stream:dead-letter - + COUNT 10
```

---

## Common Issues

### Issue 1: Redis Connection Refused

**Symptoms:**
```
Failed to connect to Redis: Connection refused
Unable to connect to Redis at localhost:6379
```

**Diagnosis:**
```bash
# Check Redis container
docker ps | grep redis

# Check Redis logs
docker logs xhs-audit-redis

# Test connection
docker exec xhs-audit-redis redis-cli ping
```

**Resolution:**
```bash
# Restart Redis container
docker-compose restart redis

# Or rebuild network
docker-compose down
docker-compose up -d
```

### Issue 2: Selenium Nodes Unavailable

**Symptoms:**
```
Could not create new session: java.net.ConnectException: Connection refused
Selenium nodes not responding
```

**Diagnosis:**
```bash
# Check Selenium Grid status
curl http://localhost:4444/status

# Check node logs
docker logs selenium-chrome-1
docker logs selenium-firefox-1
```

**Resolution:**
```bash
# Restart Selenium Grid
docker-compose -f docker-compose-selenium.yml down
docker-compose -f docker-compose-selenium.yml up -d

# Verify nodes
curl http://localhost:4444/status | jq '.nodes[].availability'
```

### Issue 3: PostgreSQL Connection Issues

**Symptoms:**
```
Connection to localhost:5432 refused
FATAL: password authentication failed
```

**Diagnosis:**
```bash
# Check PostgreSQL status
docker ps | grep postgres

# Check logs
docker logs xhs-audit-postgres

# Test connection
docker exec xhs-audit-postgres pg_isready -U postgres
```

**Resolution:**
```bash
# Restart PostgreSQL
docker-compose restart postgres

# Verify credentials in .env match docker-compose
# POSTGRES_USER=postgres, POSTGRES_PASSWORD=postgres
```

### Issue 4: High Memory Usage

**Symptoms:**
```
OutOfMemoryError: Java heap space
Container killed due to memory limit
```

**Resolution:**
```bash
# Increase JVM heap in docker-compose
environment:
  - JAVA_OPTS=-Xmx2048m -Xms1024m

# Restart with new settings
docker-compose up -d
```

### Issue 5: Message Backlog in Redis Stream

**Symptoms:**
```
XPENDING shows increasing pending messages
Crawl tasks not being processed
```

**Diagnosis:**
```bash
# Check pending messages
docker exec xhs-audit-redis redis-cli XPENDING xhs:stream:crawl

# Check consumer group status
docker exec xhs-audit-redis redis-cli XINFO GROUPS xhs:stream:crawl
```

**Resolution:**
```bash
# Restart workers to process pending messages
docker-compose restart crawler-worker-1 crawler-worker-2

# Or scale up workers
docker-compose scale crawler-worker=4
```

### Issue 6: Worker Not Processing Messages

**Symptoms:**
```
Messages stuck in stream
Workers not consuming
```

**Diagnosis:**
```bash
# Check worker logs
docker logs xhs-audit-crawler-worker

# Check consumer registration
docker exec xhs-audit-redis redis-cli XINFO GROUPS xhs:stream:crawl
```

**Resolution:**
```bash
# Restart worker with proper configuration
AUDIT_WORKER_ENABLED=true CRAWLER_WORKER_ENABLED=true docker-compose restart crawler-worker
```

---

## Rollback Procedures

### Application Rollback (Docker)

```bash
# List previous images
docker images | grep xhs-audit

# Rollback to previous image tag
docker tag xhs-audit:previous-tag xhs-audit:latest

# Restart services
docker-compose up -d
```

### Database Rollback (Flyway)

```bash
# Rollback to specific migration
docker exec xhs-audit-postgres psql -U postgres -d xhs_audit \
  -c "SELECT flyway_baseline();"

# Or manually revert via SQL
docker exec -i xhs-audit-postgres psql -U postgres xhs_audit < backup_before_migration.sql
```

### Rollback to Previous Version

```bash
# 1. Stop current services
docker-compose down

# 2. Checkout previous version
git checkout v3.0

# 3. Build previous version
mvn clean package -DskipTests

# 4. Start services
docker-compose up -d
```

---

## Performance Tuning

### JVM Settings

```yaml
environment:
  - JAVA_OPTS=-Xmx2048m -Xms1024m -XX:+UseG1GC
```

### Thread Pool Configuration (v4.0)

In `application.yml`:
```yaml
spring:
  task:
    pool:
      core-pool-size: 10
      max-pool-size: 50
      queue-capacity: 1000

# v4.0 Worker-specific pools
audit:
  crawler:
    concurrent-crawl-threads: 8
  stream-consumer:
    threads: 5
```

### Redis Tuning

```bash
# Increase max connections
docker exec xhs-audit-redis redis-cli CONFIG SET maxclients 10000

# Enable AOF persistence
docker exec xhs-audit-redis redis-cli CONFIG SET appendonly yes
```

---

## Alerting Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Memory Usage | > 70% | > 90% |
| CPU Usage | > 70% | > 90% |
| Pending Messages | > 100 | > 1000 |
| Failed Jobs/min | > 10 | > 50 |
| Response Time | > 5s | > 10s |
| Stream Lag | > 5min | > 30min |

---

## v4.0 Worker Management

### Scaling Workers

```bash
# Scale crawler workers
docker-compose scale crawler-worker=4

# Scale audit workers
docker-compose scale audit-worker=2

# Check worker status
docker-compose ps | grep worker
```

### Graceful Shutdown

```bash
# Stop accepting new messages
docker-compose stop crawler-worker

# Wait for current jobs to complete
docker logs xhs-audit-crawler-worker --since 1h | grep "completed"

# Restart
docker-compose start crawler-worker
```

### Consumer Group Management

```bash
# Create new consumer group
docker exec xhs-audit-redis redis-cli XGROUP CREATE xhs:stream:crawl new-workers $ MKSTREAM

# Remove stale consumers
docker exec xhs-audit-redis redis-cli XGROUP DELCONSUMER xhs:stream:crawl crawl-workers stale-consumer
```

---

## Emergency Procedures

### Complete System Restart

```bash
# 1. Stop all services
docker-compose down

# 2. Clear Redis keys (if needed)
docker exec xhs-audit-redis redis-cli FLUSHALL

# 3. Start all services
docker-compose up -d

# 4. Verify health
curl http://localhost:8080/actuator/health
```

### Drain and Restart Worker

```bash
# 1. Set worker to drain mode
docker-compose stop crawler-worker-1

# 2. Wait for current jobs to complete
docker logs xhs-audit-crawler-worker-1 --since 1h | grep "completed"

# 3. Restart worker
docker-compose start crawler-worker-1
```

### Emergency Stream Recovery

```bash
# 1. Identify failed messages
docker exec xhs-audit-redis redis-cli XPENDING xhs:stream:crawl - + 100

# 2. Move to dead letter queue
docker exec xhs-audit-redis redis-cli XADD xhs:stream:dead-letter * \
  job-id=$(docker exec xhs-audit-redis redis-cli XPENDING xhs:stream:crawl | jq -r '.[0]')

# 3. Restart processing
docker-compose restart crawler-worker
```

---

## Useful Commands Reference

```bash
# View all containers
docker-compose ps

# View resource usage
docker stats

# View logs with timestamp
docker logs --timestamps xhs-audit-api

# Execute in container
docker exec -it xhs-audit-api /bin/sh

# Copy files from container
docker cp xhs-audit-api:/app/logs ./local-logs

# Check network
docker network ls
docker network inspect xhs-audit-network

# Check stream consumers
docker exec xhs-audit-redis redis-cli XINFO CONSUMERS xhs:stream:crawl crawl-workers
```

---

## v4.0 Architecture Reference

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

### Stream Configuration

| Stream | Consumer Group | Purpose |
|--------|---------------|---------|
| `xhs:stream:crawl` | `crawl-workers` | URL crawling tasks |
| `xhs:stream:audit` | `audit-workers` | LLM audit tasks |
| `xhs:stream:dead-letter` | N/A | Failed message storage |

---

## Contact Information

| Role | Contact |
|------|---------|
| DevOps | [Internal] |
| On-Call | [Internal] |
| Emergency | [Internal] |

---

## Document Version

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-02-04 | - | Initial runbook |
| 2.0 | 2026-02-06 | - | Added v4.0 async worker procedures |
