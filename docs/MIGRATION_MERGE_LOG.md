# 数据库迁移脚本合并说明

## 变更概述

**日期**: 2026-02-03  
**操作**: 将 V1/V2/V3/V4 四个迁移脚本合并为单一的 V1 脚本  
**适用场景**: 开发阶段，数据可以清空重建

---

## 📁 文件变更

### 修改的文件
- ✅ `V1__initial_schema.sql` - **已更新**（包含所有功能）

### 归档的文件（移至 archive/）
- 📦 `V2__insert_sample_data.sql` - 示例数据（已合并到 V1）
- 📦 `V3__add_async_audit_fields.sql` - 异步字段（已合并到 V1）
- 📦 `V4__add_unique_constraints.sql` - 唯一约束（已合并到 V1）

---

## ✨ 合并后的 V1 功能清单

### 1. 表结构定义
- ✅ `xhs_content` - 小红书内容表
  - 包含 V1 原始字段
  - **v4.0**: 添加 `UNIQUE (post_id)` 约束
  
- ✅ `audit_result` - 审核结果表
  - 包含完整审核结果字段
  
- ✅ `audit_rule` - 审核规则表
  - 包含规则定义字段
  
- ✅ `sensitive_word` - 敏感词库表
  - 包含 V1 原始字段
  - **v4.0**: 添加 `UNIQUE (word)` 约束
  
- ✅ `audit_job` - 审核任务表
  - 包含 V1 原始字段
  - **v3**: 添加 `url` 和 `message` 字段
  - **v4.0**: 添加 `UNIQUE (job_id)` 约束

### 2. 索引定义
- ✅ 所有基础索引（V1）
- ✅ **v4.0**: 性能优化索引
  - `idx_audit_job_status`
  - `idx_audit_job_created_at`
  - `idx_xhs_content_created_at`

### 3. 示例数据
- ✅ 11 条审核规则（V2 内容，已修正字段）
- ✅ 27 条敏感词（V2 内容，已修正字段）

---

## 🔄 字段修正

在合并过程中修正了 V2 示例数据的字段名，使其与实际的 Java 实体类一致：

### audit_rule 表
| 旧字段 (V2) | 新字段 (实际) | 说明 |
|------------|-------------|------|
| `pattern_or_keyword` | `rule_content` | 规则内容 |
| `severity` | `priority` | 优先级（整数） |
| `action` | *(移除)* | 实体类中无此字段 |

**修正后的字段**:
```sql
INSERT INTO audit_rule (
    dimension,      -- title/content/tag/image
    rule_type,      -- sensitive_word/length/pattern/custom
    rule_name,      -- 规则名称
    rule_content,   -- 规则内容
    priority,       -- 优先级（100=高）
    enabled,        -- 是否启用
    description     -- 描述
)
```

### sensitive_word 表
| 旧字段 (V2) | 新字段 (实际) | 说明 |
|------------|-------------|------|
| `level` (数字) | `severity` (字符串) | 严重程度 |
| `POLITICAL/VIOLENCE/...` | `politics/violence/...` | 分类名小写 |

**修正后的值**:
- `category`: `politics`, `violence`, `adult`, `spam`, `other`
- `severity`: `high`, `medium`, `low`

---

## 🚀 使用方式

### 方案 A: 全新数据库（推荐）

如果您的开发数据库可以清空：

```bash
# 1. 删除现有数据库（可选）
docker-compose down -v

# 或手动删除
psql -U postgres -c "DROP DATABASE IF EXISTS xhs_audit;"
psql -U postgres -c "CREATE DATABASE xhs_audit;"

# 2. 删除 Flyway 历史表（如果存在）
psql -U postgres -d xhs_audit -c "DROP TABLE IF EXISTS flyway_schema_history;"

# 3. 启用 Flyway 并启动应用
docker-compose up -d
```

**配置**:
```yaml
# application-local.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    clean-disabled: false  # 开发环境允许清空
```

---

### 方案 B: 保留现有数据（需手动处理）

如果您已经有 V1/V2/V3 的数据不想清空：

```bash
# 1. 备份现有数据
pg_dump -U postgres xhs_audit > backup_before_merge.sql

# 2. 手动执行 V3 和 V4 的变更（如果尚未执行）
psql -U postgres -d xhs_audit << EOF

-- V3: 添加字段
ALTER TABLE audit_job ADD COLUMN IF NOT EXISTS url VARCHAR(500);
ALTER TABLE audit_job ADD COLUMN IF NOT EXISTS message VARCHAR(500);

-- V4: 添加唯一约束（需要先清理重复数据）
-- 清理重复的 post_id
WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY post_id ORDER BY created_at ASC) as rn
    FROM xhs_content
)
DELETE FROM xhs_content WHERE id IN (SELECT id FROM duplicates WHERE rn > 1);

-- 清理重复的 job_id
WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY job_id ORDER BY created_at ASC) as rn
    FROM audit_job
)
DELETE FROM audit_job WHERE id IN (SELECT id FROM duplicates WHERE rn > 1);

-- 添加唯一约束
ALTER TABLE audit_job ADD CONSTRAINT uk_audit_job_job_id UNIQUE (job_id);
ALTER TABLE xhs_content ADD CONSTRAINT uk_xhs_content_post_id UNIQUE (post_id);

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_audit_job_status ON audit_job(status);
CREATE INDEX IF NOT EXISTS idx_audit_job_created_at ON audit_job(created_at);
CREATE INDEX IF NOT EXISTS idx_xhs_content_created_at ON xhs_content(created_at);

EOF

# 3. 更新 Flyway 历史表（标记为已执行）
psql -U postgres -d xhs_audit << EOF
DELETE FROM flyway_schema_history WHERE version IN ('2', '3', '4');
INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, 
    checksum, installed_by, installed_on, execution_time, success
) VALUES (
    1, '1', 'initial schema', 'SQL', 'V1__initial_schema.sql',
    0, 'postgres', CURRENT_TIMESTAMP, 0, true
);
EOF
```

---

## 🧪 验证数据库

执行以下 SQL 验证迁移是否成功：

```sql
-- 1. 检查表是否存在
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- 预期输出: 
-- audit_job, audit_result, audit_rule, sensitive_word, xhs_content

-- 2. 检查唯一约束
SELECT conname, conrelid::regclass 
FROM pg_constraint 
WHERE contype = 'u' 
  AND connamespace = 'public'::regnamespace;

-- 预期输出:
-- uk_audit_job_job_id     | audit_job
-- uk_xhs_content_post_id  | xhs_content
-- uk_sensitive_word       | sensitive_word

-- 3. 检查 audit_job 的新字段
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'audit_job' 
  AND column_name IN ('url', 'message');

-- 预期输出:
-- url     | character varying
-- message | character varying

-- 4. 检查索引
SELECT indexname, tablename 
FROM pg_indexes 
WHERE schemaname = 'public' 
  AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;

-- 5. 检查示例数据
SELECT COUNT(*) as audit_rules FROM audit_rule;
SELECT COUNT(*) as sensitive_words FROM sensitive_word;

-- 预期输出:
-- audit_rules: 11
-- sensitive_words: 27

-- 6. 查看按类别分组的敏感词
SELECT category, COUNT(*) as count 
FROM sensitive_word 
GROUP BY category 
ORDER BY count DESC;
```

---

## ⚠️ 注意事项

### 1. Flyway 校验和变更
合并后的 V1 文件内容已变更，Flyway 的 checksum 会不匹配。

**解决方案**:
- **开发环境**: 删除数据库，从头开始（推荐）
- **生产环境**: **不要使用合并脚本**，继续使用 V1/V2/V3/V4

### 2. 旧脚本归档
旧的 V2/V3/V4 已移至 `archive/` 目录，不会被 Flyway 执行。

如需恢复：
```bash
mv archive/V2__insert_sample_data.sql .
mv archive/V3__add_async_audit_fields.sql .
mv archive/V4__add_unique_constraints.sql .
```

### 3. 示例数据调整
如果不需要示例数据，可以删除 V1 脚本底部的 INSERT 语句。

---

## 📊 迁移前后对比

| 项目 | 迁移前 | 迁移后 |
|------|-------|-------|
| 脚本文件数 | 4 个 (V1~V4) | 1 个 (V1) |
| 执行次数 | 4 次 | 1 次 |
| 初始化时间 | ~2-3 秒 | ~1 秒 |
| 表结构 | 相同 | 相同 |
| 唯一约束 | ✅ | ✅ |
| 示例数据 | ✅ | ✅ |

---

## 🔙 回滚方案

如果合并后出现问题，可以恢复旧的脚本：

```bash
# 1. 恢复归档的脚本
cd src/main/resources/db/migration
mv archive/*.sql .

# 2. 恢复 V1 原始内容（从 Git）
git checkout HEAD -- V1__initial_schema.sql

# 3. 删除数据库重新初始化
docker-compose down -v
docker-compose up -d
```

---

## ✅ 检查清单

部署前请确认：

- [ ] 备份了现有数据（如需保留）
- [ ] 检查了实体类字段与脚本是否匹配
- [ ] 确认开发环境可以清空数据库
- [ ] 测试了合并后的脚本执行
- [ ] 验证了示例数据是否正确插入
- [ ] 确认了唯一约束是否生效

---

## 📝 后续建议

1. **生产环境**: 继续使用原始的 V1/V2/V3/V4 增量迁移
2. **新功能**: 创建 V5, V6... 增量脚本
3. **定期审查**: 每个季度整理一次迁移脚本
4. **文档更新**: 保持数据库文档与脚本同步

---

## 📞 支持

如有问题，请参考：
- [数据库迁移安全指南](./DATABASE_MIGRATION_GUIDE.md)
- [多节点部署文档](./MULTI_NODE_DEPLOYMENT.md)
- [Flyway 官方文档](https://flywaydb.org/documentation/)
