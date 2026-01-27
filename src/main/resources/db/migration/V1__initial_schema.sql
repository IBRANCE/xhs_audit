-- V1__initial_schema.sql
-- 初始数据库表结构

-- 小红书内容表
CREATE TABLE IF NOT EXISTS xhs_content (
    id BIGSERIAL PRIMARY KEY,
    post_id VARCHAR(100) UNIQUE NOT NULL,
    url VARCHAR(500) NOT NULL,
    title VARCHAR(500),
    content TEXT,
    images JSONB,
    tags JSONB,
    metadata JSONB,
    author_id VARCHAR(100),
    published_at TIMESTAMP,
    crawled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审核结果表
CREATE TABLE IF NOT EXISTS audit_result (
    id BIGSERIAL PRIMARY KEY,
    post_id VARCHAR(100) NOT NULL,
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
    word VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50),
    severity VARCHAR(20),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审核任务表
CREATE TABLE IF NOT EXISTS audit_job (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) UNIQUE NOT NULL,
    total_links INT,
    completed_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    status VARCHAR(20),
    file_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_xhs_content_post_id ON xhs_content(post_id);
CREATE INDEX idx_xhs_content_url ON xhs_content(url);
CREATE INDEX idx_xhs_content_author_id ON xhs_content(author_id);
CREATE INDEX idx_xhs_content_published_at ON xhs_content(published_at);
CREATE INDEX idx_xhs_content_crawled_at ON xhs_content(crawled_at DESC);
CREATE INDEX idx_xhs_content_tags_gin ON xhs_content USING GIN(tags);
CREATE INDEX idx_xhs_content_metadata_gin ON xhs_content USING GIN(metadata);

CREATE INDEX idx_audit_result_post_id ON audit_result(post_id);
CREATE INDEX idx_audit_result_job_id ON audit_result(job_id);
CREATE INDEX idx_audit_result_audit_status ON audit_result(audit_status);
CREATE INDEX idx_audit_result_audited_at ON audit_result(audited_at DESC);

CREATE INDEX idx_audit_rule_dimension ON audit_rule(dimension);
CREATE INDEX idx_audit_rule_enabled ON audit_rule(enabled);
CREATE INDEX idx_audit_rule_priority ON audit_rule(priority);

CREATE INDEX idx_sensitive_word_word ON sensitive_word(word);
CREATE INDEX idx_sensitive_word_category ON sensitive_word(category);
CREATE INDEX idx_sensitive_word_severity ON sensitive_word(severity);

CREATE INDEX idx_audit_job_job_id ON audit_job(job_id);
CREATE INDEX idx_audit_job_status ON audit_job(status);
CREATE INDEX idx_audit_job_created_at ON audit_job(created_at DESC);
