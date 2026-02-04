# 数据库迁移安全指南

## Flyway 版本管理机制

### 📋 迁移脚本清单

| 版本 | 文件 | 作用 | 数据影响 |
|------|------|------|---------|
| V1 | `V1__initial_schema.sql` | 创建表结构 | ✅ 安全（CREATE IF NOT EXISTS） |
| V2 | `V2__insert_sample_data.sql` | 插入示例数据 | ✅ 安全（INSERT） |
| V3 | `V3__add_async_audit_fields.sql` | 添加异步审核字段 | ✅ 安全（ALTER ADD） |
| V4 | `V4__add_unique_constraints.sql` | 添加唯一约束 | ⚠️ 需确认无重复数据 |

---

## 🔐 安全机制

### 1. Flyway 版本控制表

Flyway 自动创建 `flyway_schema_history` 表记录已执行的脚本：

```sql
-- 查看已执行的迁移
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- 示例输出
 installed_rank | version | description              | type | script                           | installed_on         | success 
----------------|---------|--------------------------|------|----------------------------------|---------------------|--------
              1 | 1       | initial schema           | SQL  | V1__initial_schema.sql           | 2026-01-01 10:00:00 | t
              2 | 2       | insert sample data       | SQL  | V2__insert_sample_data.sql       | 2026-01-01 10:00:05 | t
              3 | 3       | add async audit fields   | SQL  | V3__add_async_audit_fields.sql   | 2026-01-02 15:30:00 | t
```

### 2. 核心规则

✅ **只执行未执行的脚本**
- 已记录的脚本永远不会重复执行
- 新增脚本（如 V4）只在首次启动时执行

✅ **版本号递增**
- V1 → V2 → V3 → V4
- 不能插入中间版本（如 V2.5）

✅ **校验和验证**
- 已执行脚本的内容不能修改
- 修改会导致启动失败（checksum 不匹配）

---

## 🚀 启动场景分析

### 场景 A：全新数据库

**状态：** 数据库为空

**执行流程：**
```bash
应用启动
  ↓
Flyway 检查: flyway_schema_history 不存在
  ↓
顺序执行: V1 → V2 → V3 → V4
  ↓
创建完整表结构 + 示例数据 + 唯一约束
```

**结果：** ✅ 成功，数据库初始化完成

---

### 场景 B：已有数据库（执行过 V1, V2, V3）

**状态：** 
```sql
SELECT version FROM flyway_schema_history;
-- 1, 2, 3
```

**执行流程：**
```bash
应用启动
  ↓
Flyway 检查: 已执行 V1, V2, V3
  ↓
跳过: V1, V2, V3
  ↓
执行: V4（添加唯一约束）
  ↓
检查是否有重复数据
  ├─ 无重复 → ✅ 成功添加约束
  └─ 有重复 → ❌ 失败（见下文处理方案）
```

**结果：** 
- ✅ 如果无重复数据：成功
- ⚠️ 如果有重复数据：需要先清理（V4 已包含自动清理逻辑）

---

### 场景 C：完全同步（执行过 V1, V2, V3, V4）

**状态：**
```sql
SELECT version FROM flyway_schema_history;
-- 1, 2, 3, 4
```

**执行流程：**
```bash
应用启动
  ↓
Flyway 检查: 已执行 V1, V2, V3, V4
  ↓
跳过: 所有脚本
  ↓
应用正常启动
```

**结果：** ✅ 成功，无任何数据变更

---

## ⚠️ V4 风险与处理

### 风险：重复数据冲突

如果表中已存在重复的 `post_id` 或 `job_id`，添加唯一约束会失败：

```sql
ERROR:  could not create unique index "uk_xhs_content_post_id"
DETAIL:  Key (post_id)=(xxx) is duplicated.
```

### 解决方案（已集成到 V4）

V4 脚本已内置自动清理逻辑：

```sql
-- 自动清理重复数据（保留最早的记录）
WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY post_id ORDER BY created_at ASC) as rn
    FROM xhs_content
)
DELETE FROM xhs_content WHERE id IN (SELECT id FROM duplicates WHERE rn > 1);
```

**逻辑：**
1. 查找重复的 `post_id`
2. 按 `created_at` 排序，保留最早的记录
3. 删除后续重复记录
4. 添加唯一约束

---

## 🛡️ 安全检查清单

### 执行 V4 前的检查

```sql
-- 1. 检查是否有重复的 post_id
SELECT post_id, COUNT(*) as count
FROM xhs_content
GROUP BY post_id
HAVING COUNT(*) > 1;

-- 2. 检查是否有重复的 job_id
SELECT job_id, COUNT(*) as count
FROM audit_job
GROUP BY job_id
HAVING COUNT(*) > 1;

-- 3. 检查是否有重复的 url（如果启用 URL 唯一约束）
SELECT url, COUNT(*) as count
FROM xhs_content
GROUP BY url
HAVING COUNT(*) > 1;
```

**如果有重复数据：** V4 会自动清理（保留最早记录）

---

## 🔧 常见操作

### 1. 启用 Flyway

**开发环境（自动迁移）：**
```yaml
# application-local.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true  # 允许在已有数据库上执行
```

**生产环境（手动迁移）：**
```bash
# 使用独立命令执行迁移
docker run --rm \
  --network xhs-audit-network \
  -e SPRING_PROFILES_ACTIVE=production \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/xhs_audit \
  xhs-audit:latest \
  java -jar app.jar --spring.flyway.enabled=true --spring.flyway.migrate=true
```

### 2. 查看迁移状态

```sql
-- 查看所有已执行的迁移
SELECT 
    installed_rank,
    version,
    description,
    script,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### 3. 回滚（Flyway 不支持自动回滚）

Flyway 不支持自动回滚，需要手动创建"撤销"脚本：

```sql
-- V5__rollback_unique_constraints.sql（手动创建）
ALTER TABLE audit_job DROP CONSTRAINT IF EXISTS uk_audit_job_job_id;
ALTER TABLE xhs_content DROP CONSTRAINT IF EXISTS uk_xhs_content_post_id;
ALTER TABLE xhs_content DROP CONSTRAINT IF EXISTS uk_xhs_content_url;
```

### 4. 修复失败的迁移

```sql
-- 如果迁移失败，手动修复后删除失败记录
DELETE FROM flyway_schema_history WHERE version = '4' AND success = false;

-- 然后重新启动应用
```

---

## 📌 最佳实践

### 1. 迁移脚本规范

✅ **DO（推荐）**
- 使用 `IF NOT EXISTS` 避免重复创建
- 使用 `DO $$ ... END $$;` 处理条件逻辑
- 在 ALTER TABLE 前清理冲突数据
- 添加详细注释说明变更原因

❌ **DON'T（避免）**
- 不要修改已执行的脚本
- 不要使用 `DROP TABLE` 删除数据
- 不要在生产环境直接启用 `flyway.enabled=true`
- 不要跳过版本号（如 V1, V3, V5）

### 2. 部署流程

```bash
# Step 1: 备份数据库
pg_dump -U postgres xhs_audit > backup_$(date +%Y%m%d).sql

# Step 2: 在测试环境验证迁移
docker-compose -f docker-compose-test.yml up -d
# 查看日志确认迁移成功

# Step 3: 在生产环境执行迁移
docker-compose -f docker-compose-production.yml up -d
# 监控日志和健康检查

# Step 4: 验证数据完整性
psql -U postgres -d xhs_audit -c "SELECT COUNT(*) FROM xhs_content;"
```

### 3. 监控迁移

```sql
-- 检查最后一次迁移
SELECT 
    version,
    description,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;

-- 检查是否有失败的迁移
SELECT * FROM flyway_schema_history WHERE success = false;
```

---

## 🔍 故障排查

### 问题 1: Checksum 不匹配

**错误：**
```
Migration checksum mismatch for migration version 1
Expected: 123456789
Actual: 987654321
```

**原因：** 已执行的脚本被修改

**解决：**
```sql
-- 方案 A: 更新 checksum（不推荐）
UPDATE flyway_schema_history 
SET checksum = 987654321 
WHERE version = '1';

-- 方案 B: 创建新版本脚本（推荐）
-- 创建 V5__fix_xxx.sql 实现修改
```

### 问题 2: 重复约束冲突

**错误：**
```
ERROR: duplicate key value violates unique constraint "uk_xhs_content_post_id"
```

**解决：** V4 已自动清理，如果仍失败：

```sql
-- 手动清理重复数据
WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY post_id ORDER BY created_at) as rn
    FROM xhs_content
)
DELETE FROM xhs_content WHERE id IN (SELECT id FROM duplicates WHERE rn > 1);

-- 删除失败的迁移记录
DELETE FROM flyway_schema_history WHERE version = '4';

-- 重新启动应用
```

### 问题 3: 迁移卡死

**现象：** 应用启动时卡在迁移阶段

**排查：**
```sql
-- 查看正在执行的 SQL
SELECT 
    pid,
    usename,
    application_name,
    state,
    query,
    query_start
FROM pg_stat_activity
WHERE datname = 'xhs_audit';

-- 杀死卡死的连接
SELECT pg_terminate_backend(pid) WHERE pid = 12345;
```

---

## 📝 总结

### 核心要点

1. ✅ **Flyway 默认禁用**：不会自动执行迁移
2. ✅ **已执行脚本不会重复**：数据安全有保障
3. ✅ **V4 安全增强**：自动清理重复数据
4. ✅ **生产环境手动迁移**：可控性更强

### 数据安全保证

| 场景 | 是否安全 | 说明 |
|------|---------|------|
| 首次启动（空数据库） | ✅ 完全安全 | 创建全新表结构 |
| 增量迁移（已有 V1-V3） | ✅ 完全安全 | 只执行 V4，不删除数据 |
| 重复启动（已有 V1-V4） | ✅ 完全安全 | 跳过所有脚本 |
| 有重复数据 | ⚠️ 会清理重复 | V4 保留最早记录 |

### 推荐配置

```yaml
# 开发环境：自动迁移
spring.flyway.enabled: true

# 生产环境：禁用自动迁移，手动执行
spring.flyway.enabled: false
```

---

## 📚 参考资料

- [Flyway 官方文档](https://flywaydb.org/documentation/)
- [PostgreSQL 约束管理](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [数据库版本控制最佳实践](https://www.redgate.com/hub/university/courses/flyway)
