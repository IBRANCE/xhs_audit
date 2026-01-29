package com.xhs.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 审核 Prompt 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "audit.prompts")
public class AuditPromptConfig {

    /**
     * 文本审核 System Prompt
     */
    private String textSystem;

    /**
     * 图片审核提示词
     */
    private String textImage;

    /**
     * 图片审核通过的关键词（用于判断结果）
     */
    private String imagePassKeywords = "是";
}
