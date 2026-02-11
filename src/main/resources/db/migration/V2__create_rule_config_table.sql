-- V2__create_rule_config_table.sql
-- 新增内容基础规则配置表

CREATE TABLE IF NOT EXISTS audit_rule_config (
    id BIGINT PRIMARY KEY,
    min_text_length INT NOT NULL,
    min_image_count INT NOT NULL,
    required_tags JSONB NOT NULL,
    car_model_names JSONB NOT NULL,
    excluded_tags JSONB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO audit_rule_config (
    id,
    min_text_length,
    min_image_count,
    required_tags,
    car_model_names,
    excluded_tags,
    updated_at)
VALUES (
    1,
    25,
    1,
    '["东风日产","尽兴由NI"]'::jsonb,
    '["天籁","轩逸","逍客","奇骏","X-TRAIL","ARIYA","艾睿雅","N7","N6","NX8","探陆","NISSAN","启辰大V","启辰星","启辰D60","启辰","Venucia","QX50","QX60","Q50L","英菲尼迪","INFINITI"]'::jsonb,
    '["东风日产","尽兴由NI"]'::jsonb,
    CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
