-- V1__initial_schema.sql
-- 完整数据库初始化脚本（合并 V1/V2/V3/V4）
-- 开发阶段使用：包含表结构、索引、唯一约束、示例数据
-- 最后更新: 2026-02-03

-- ==========================================
-- 表结构定义
-- ==========================================

-- 小红书内容表
CREATE TABLE IF NOT EXISTS xhs_content (
    id BIGSERIAL PRIMARY KEY,
    post_id VARCHAR(256) NOT NULL,
    url VARCHAR(500) NOT NULL,
    title VARCHAR(500),
    content TEXT,
    images JSONB,
    tags JSONB,
    metadata JSONB,
    author_id VARCHAR(256),
    published_at TIMESTAMP,
    crawled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INT DEFAULT 0 NOT NULL,  -- v4.0: 乐观锁版本号
    -- v4.0: 唯一约束（防止重复消费）
    CONSTRAINT uk_xhs_content_post_id UNIQUE (post_id)
);

-- 审核结果表
CREATE TABLE IF NOT EXISTS audit_result (
    id BIGSERIAL PRIMARY KEY,
    post_id VARCHAR(256) NOT NULL,
    url VARCHAR(500) NOT NULL,
    job_id VARCHAR(100),
    audit_status VARCHAR(20),
    reasons JSONB,
    confidence_score DECIMAL(3, 2),
    model_name VARCHAR(100),
    audited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审核规则表
CREATE TABLE IF NOT EXISTS audit_rule (
    id BIGSERIAL PRIMARY KEY,
    dimension VARCHAR(50) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    rule_name VARCHAR(200),
    rule_content TEXT NOT NULL,
    priority INT DEFAULT 100,
    enabled BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 敏感词库表
CREATE TABLE IF NOT EXISTS sensitive_word (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    severity VARCHAR(20),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sensitive_word UNIQUE (word)
);

-- 审核任务表
CREATE TABLE IF NOT EXISTS audit_job (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    total_links INT,
    completed_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    status VARCHAR(20),
    file_name VARCHAR(255),
    url VARCHAR(500),          -- v3: 单个任务的URL
    message VARCHAR(500),       -- v3: 任务状态消息
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    version INT DEFAULT 0 NOT NULL,  -- v4.0: 乐观锁版本号
    -- v4.0: 唯一约束（防止重复创建任务）
    CONSTRAINT uk_audit_job_job_id UNIQUE (job_id)
);

-- ==========================================
-- 索引定义
-- ==========================================

-- xhs_content 表索引
CREATE INDEX IF NOT EXISTS idx_xhs_content_post_id ON xhs_content(post_id);
CREATE INDEX IF NOT EXISTS idx_xhs_content_url ON xhs_content(url);
CREATE INDEX IF NOT EXISTS idx_xhs_content_author_id ON xhs_content(author_id);
CREATE INDEX IF NOT EXISTS idx_xhs_content_published_at ON xhs_content(published_at);
CREATE INDEX IF NOT EXISTS idx_xhs_content_crawled_at ON xhs_content(crawled_at DESC);
CREATE INDEX IF NOT EXISTS idx_xhs_content_created_at ON xhs_content(created_at);  -- v4.0
CREATE INDEX IF NOT EXISTS idx_xhs_content_tags_gin ON xhs_content USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_xhs_content_metadata_gin ON xhs_content USING GIN(metadata);

-- audit_result 表索引
CREATE INDEX IF NOT EXISTS idx_audit_result_post_id ON audit_result(post_id);
CREATE INDEX IF NOT EXISTS idx_audit_result_job_id ON audit_result(job_id);
CREATE INDEX IF NOT EXISTS idx_audit_result_audit_status ON audit_result(audit_status);
CREATE INDEX IF NOT EXISTS idx_audit_result_audited_at ON audit_result(audited_at DESC);

-- audit_rule 表索引
CREATE INDEX IF NOT EXISTS idx_audit_rule_dimension ON audit_rule(dimension);
CREATE INDEX IF NOT EXISTS idx_audit_rule_enabled ON audit_rule(enabled);
CREATE INDEX IF NOT EXISTS idx_audit_rule_priority ON audit_rule(priority);

-- sensitive_word 表索引
CREATE INDEX IF NOT EXISTS idx_sensitive_word_word ON sensitive_word(word);
CREATE INDEX IF NOT EXISTS idx_sensitive_word_category ON sensitive_word(category);
CREATE INDEX IF NOT EXISTS idx_sensitive_word_severity ON sensitive_word(severity);

-- audit_job 表索引
CREATE INDEX IF NOT EXISTS idx_audit_job_job_id ON audit_job(job_id);
CREATE INDEX IF NOT EXISTS idx_audit_job_status ON audit_job(status);             -- v4.0
CREATE INDEX IF NOT EXISTS idx_audit_job_created_at ON audit_job(created_at DESC);  -- v4.0

-- ==========================================
-- 注释说明
-- ==========================================

COMMENT ON TABLE xhs_content IS '小红书内容表';
COMMENT ON TABLE audit_result IS '审核结果表';
COMMENT ON TABLE audit_rule IS '审核规则表';
COMMENT ON TABLE sensitive_word IS '敏感词库表';
COMMENT ON TABLE audit_job IS '审核任务表';

COMMENT ON COLUMN audit_job.url IS '单个任务的URL（用于异步审核API）';
COMMENT ON COLUMN audit_job.message IS '任务状态消息（如：爬取中、审核中等）';
COMMENT ON COLUMN audit_job.version IS 'v4.0: 乐观锁版本号，每次更新自增';
COMMENT ON COLUMN xhs_content.version IS 'v4.0: 乐观锁版本号，每次更新自增';
COMMENT ON CONSTRAINT uk_audit_job_job_id ON audit_job IS 'v4.0: 防止重复创建任务';
COMMENT ON CONSTRAINT uk_xhs_content_post_id ON xhs_content IS 'v4.0: 防止重复爬取同一内容';

-- ==========================================
-- 示例数据（开发环境使用）
-- ==========================================

-- 注意：以下示例数据仅用于开发测试，生产环境请根据实际需求配置

-- 审核规则示例数据（与 AuditRule 实体类匹配）
-- 字段：dimension, rule_type, rule_name, rule_content, priority, enabled, description
INSERT INTO audit_rule (dimension, rule_type, rule_name, rule_content, priority, enabled, description, created_at, updated_at)
VALUES 
    ('content', 'sensitive_word', '政治敏感词检测', '政治敏感', 100, true, '检测政治敏感词汇', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'sensitive_word', '暴力内容检测', '暴力内容', 100, true, '检测暴力相关内容', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'sensitive_word', '色情低俗检测', '色情低俗', 100, true, '检测色情低俗内容', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'sensitive_word', '违禁品检测', '违禁品', 100, true, '检测违禁品相关内容', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'pattern', '虚假宣传检测', '虚假宣传', 80, true, '检测虚假医疗、减肥等宣传', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'pattern', '刷屏行为检测', '刷屏行为', 60, true, '检测重复发布相同内容', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('title', 'pattern', '诱导点击检测', '诱导点击', 70, true, '检测诱导点赞、关注等行为', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'custom', '点赞异常检测', '点赞异常', 70, true, '短时间内获得大量点赞', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'custom', '评论异常检测', '评论异常', 60, true, '评论内容高度相似', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('content', 'pattern', '侵权内容检测', '侵权内容', 75, true, '检测可能的侵权内容', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('image', 'custom', '盗图行为检测', '盗图行为', 75, true, '检测盗用他人图片', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 敏感词库示例数据（与 SensitiveWord 实体类匹配）
-- 字段：word, category, severity, enabled
INSERT INTO sensitive_word (word, category, severity, enabled, created_at)
VALUES 
    -- 政治敏感词
    ('政治敏感词1', 'politics', 'high', true, CURRENT_TIMESTAMP),
    ('政治敏感词2', 'politics', 'high', true, CURRENT_TIMESTAMP),
    ('政治敏感词3', 'politics', 'medium', true, CURRENT_TIMESTAMP),
    -- 暴力词汇
    ('暴力词汇1', 'violence', 'high', true, CURRENT_TIMESTAMP),
    ('暴力词汇2', 'violence', 'medium', true, CURRENT_TIMESTAMP),
    ('血腥', 'violence', 'medium', true, CURRENT_TIMESTAMP),
    ('杀害', 'violence', 'high', true, CURRENT_TIMESTAMP),
    -- 色情低俗词汇
    ('色情词汇1', 'adult', 'high', true, CURRENT_TIMESTAMP),
    ('色情词汇2', 'adult', 'high', true, CURRENT_TIMESTAMP),
    ('低俗词汇', 'adult', 'low', true, CURRENT_TIMESTAMP),
    -- 违禁品关键词
    ('毒品', 'violence', 'high', true, CURRENT_TIMESTAMP),
    ('枪支', 'violence', 'high', true, CURRENT_TIMESTAMP),
    ('爆炸物', 'violence', 'high', true, CURRENT_TIMESTAMP),
    ('假币', 'other', 'high', true, CURRENT_TIMESTAMP),
    -- 虚假宣传关键词
    ('包治百病', 'spam', 'medium', true, CURRENT_TIMESTAMP),
    ('一周瘦20斤', 'spam', 'medium', true, CURRENT_TIMESTAMP),
    ('月入百万', 'spam', 'low', true, CURRENT_TIMESTAMP),
    ('零风险高回报', 'spam', 'medium', true, CURRENT_TIMESTAMP),
    ('躺赚', 'spam', 'low', true, CURRENT_TIMESTAMP),
    -- 诈骗关键词
    ('转账', 'spam', 'low', true, CURRENT_TIMESTAMP),
    ('兼职刷单', 'spam', 'medium', true, CURRENT_TIMESTAMP),
    ('免费领取', 'spam', 'low', true, CURRENT_TIMESTAMP),
    ('中奖信息', 'spam', 'low', true, CURRENT_TIMESTAMP),
    -- 垃圾信息关键词
    ('广告引流', 'spam', 'low', true, CURRENT_TIMESTAMP),
    ('微信号', 'spam', 'low', true, CURRENT_TIMESTAMP),
    ('加我私聊', 'spam', 'low', true, CURRENT_TIMESTAMP)
ON CONFLICT (word) DO NOTHING;
