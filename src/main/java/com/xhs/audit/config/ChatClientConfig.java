package com.xhs.audit.config;

import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient配置类
 * 配置LLM客户端、对话记忆和Function Calling工具注册
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    /**
     * 创建OpenAiChatClient Bean
     * Spring AI 0.8.1版本的API
     */
    @Bean
    public OpenAiChatClient openAiChatClient() {
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);
        return new OpenAiChatClient(openAiApi);
    }
}
