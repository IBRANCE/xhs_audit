-- V2__insert_sample_data.sql
-- 插入示例审核规则和敏感词数据

-- ==========================================
-- 审核规则示例数据 (AuditRule)
-- ==========================================

-- 1. 敏感词规则
INSERT INTO audit_rule (rule_type, pattern_or_keyword, severity, action, description, enabled, created_at, updated_at)
VALUES 
    ('SENSITIVE_WORDS', '政治敏感', 'HIGH', 'REJECT', '检测政治敏感词汇', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('SENSITIVE_WORDS', '暴力内容', 'HIGH', 'REJECT', '检测暴力相关内容', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('SENSITIVE_WORDS', '色情低俗', 'HIGH', 'REJECT', '检测色情低俗内容', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('SENSITIVE_WORDS', '违禁品', 'HIGH', 'REJECT', '检测违禁品相关内容', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 2. 内容模式规则
INSERT INTO audit_rule (rule_type, pattern_or_keyword, severity, action, description, enabled, created_at, updated_at)
VALUES 
    ('CONTENT_PATTERN', '虚假宣传', 'MEDIUM', 'FLAG', '检测虚假医疗、减肥等宣传', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CONTENT_PATTERN', '刷屏行为', 'LOW', 'FLAG', '检测重复发布相同内容', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CONTENT_PATTERN', '诱导点击', 'MEDIUM', 'FLAG', '检测诱导点赞、关注等行为', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 3. 互动异常规则
INSERT INTO audit_rule (rule_type, pattern_or_keyword, severity, action, description, enabled, created_at, updated_at)
VALUES 
    ('ENGAGEMENT_ANOMALY', '点赞异常', 'MEDIUM', 'REVIEW', '短时间内获得大量点赞', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ENGAGEMENT_ANOMALY', '评论异常', 'LOW', 'REVIEW', '评论内容高度相似', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 4. 知识产权规则
INSERT INTO audit_rule (rule_type, pattern_or_keyword, severity, action, description, enabled, created_at, updated_at)
VALUES 
    ('COPYRIGHT', '侵权内容', 'MEDIUM', 'FLAG', '检测可能的侵权内容', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('COPYRIGHT', '盗图行为', 'MEDIUM', 'FLAG', '检测盗用他人图片', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ==========================================
-- 敏感词库示例数据 (SensitiveWord)
-- ==========================================

-- 政治敏感词 (示例，实际应该更全面)
INSERT INTO sensitive_word (word, category, level, created_at)
VALUES 
    ('政治敏感词1', 'POLITICAL', 5, CURRENT_TIMESTAMP),
    ('政治敏感词2', 'POLITICAL', 5, CURRENT_TIMESTAMP),
    ('政治敏感词3', 'POLITICAL', 4, CURRENT_TIMESTAMP);

-- 暴力词汇
INSERT INTO sensitive_word (word, category, level, created_at)
VALUES 
    ('暴力词汇1', 'VIOLENCE', 5, CURRENT_TIMESTAMP),
    ('暴力词汇2', 'VIOLENCE', 4, CURRENT_TIMESTAMP),
    ('血腥', 'VIOLENCE', 4, CURRENT_TIMESTAMP),
    ('杀害', 'VIOLENCE', 5, CURRENT_TIMESTAMP);

-- 色情低俗词汇
INSERT INTO sensitive_word (word, category, level, created_at)
VALUES 
    ('色情词汇1', 'PORNOGRAPHY', 5, CURRENT_TIMESTAMP),
    ('色情词汇2', 'PORNOGRAPHY', 5, CURRENT_TIMESTAMP),
    ('低俗词汇', 'PORNOGRAPHY', 3, CURRENT_TIMESTAMP);

-- 违禁品关键词
INSERT INTO sensitive_word (word, category, level, created_at)
VALUES 
    ('毒品', 'PROHIBITED', 5, CURRENT_TIMESTAMP),
    ('枪支', 'PROHIBITED', 5, CURRENT_TIMESTAMP),
    ('爆炸物', 'PROHIBITED', 5, CURRENT_TIMESTAMP),
    ('假币', 'PROHIBITED', 5, CURRENT_TIMESTAMP);

-- 虚假宣传关键词
INSERT INTO sensitive_word (word, category, level, created_at)
VALUES 
    ('包治百病', 'FALSE_ADVERTISING', 4, CURRENT_TIMESTAMP),
    ('一周瘦20斤', 'FALSE_ADVERTISING', 4, CURRENT_TIMESTAMP),
    ('月入百万', 'FALSE_ADVERTISING', 3, CURRENT_TIMESTAMP),
    ('零风险高回报', 'FALSE_ADVERTISING', 4, CURRENT_TIMESTAMP),
    ('躺赚', 'FALSE_ADVERTISING', 3, CURRENT_TIMESTAMP);

-- 诈骗关键词
INSERT INTO sensitive_word (word, category, level, created_at)
VALUES 
    ('转账', 'FRAUD', 3, CURRENT_TIMESTAMP),
    ('兼职刷单', 'FRAUD', 4, CURRENT_TIMESTAMP),
    ('免费领取', 'FRAUD', 2, CURRENT_TIMESTAMP),
    ('中奖信息', 'FRAUD', 3, CURRENT_TIMESTAMP);

-- 其他违规词汇
INSERT INTO sensitive_word (word, category, level, created_at)
VALUES 
    ('广告引流', 'SPAM', 2, CURRENT_TIMESTAMP),
    ('微信号', 'SPAM', 2, CURRENT_TIMESTAMP),
    ('加我私聊', 'SPAM', 2, CURRENT_TIMESTAMP);

-- ==========================================
-- 统计信息
-- ==========================================

-- 查看插入的数据统计
-- SELECT rule_type, COUNT(*) as count FROM audit_rule WHERE enabled = true GROUP BY rule_type;
-- SELECT category, COUNT(*) as count FROM sensitive_word GROUP BY category;

-- 预期结果:
-- AuditRule: 11条规则
--   - SENSITIVE_WORDS: 4条
--   - CONTENT_PATTERN: 3条
--   - ENGAGEMENT_ANOMALY: 2条
--   - COPYRIGHT: 2条
-- 
-- SensitiveWord: 29条敏感词
--   - POLITICAL: 3条
--   - VIOLENCE: 4条
--   - PORNOGRAPHY: 3条
--   - PROHIBITED: 4条
--   - FALSE_ADVERTISING: 5条
--   - FRAUD: 4条
--   - SPAM: 3条
--   - OTHER: 3条
