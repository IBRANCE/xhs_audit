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
- Node availability
- Browser versions

![Selenium Grid Dashboard](SELENIUM_MONITORING.md)

### Redis Commander

Access Redis GUI at: **http://localhost:8081**

Monitor:
- Stream message counts
- Key space usage
- Consumer group status

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
docker exec xhs-audit-redis redis-cli XPENDING crawl:tasks

# Check consumer group status
docker exec xhs-audit-redis redis-cli XINFO GROUPS crawl:tasks
```

**Resolution:**
```bash
# Restart workers to process pending messages
docker-compose restart crawler-worker-1 crawler-worker-2

# Or scale up workers
docker-compose scale crawler-worker=4
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

### Thread Pool Configuration

In `application.yml`:
```yaml
spring:
  task:
    pool:
      core-pool-size: 10-size: 50
      max-pool
      queue-capacity: 1000
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
docker logs xhs-audit-crawler-1 --since 1h | grep "completed"

# 3. Restart worker
docker-compose start crawler-worker-1
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
```

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
