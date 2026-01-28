package com.xhs.audit.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ChatClient配置类
 * 配置LLM客户端、对话记忆和Function Calling工具注册
 * 支持本地部署的文本模型和图像模型
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.model:gpt-4-turbo}")
    private String chatModel;

    @Value("${spring.ai.openai.chat.temperature:0.3}")
    private Double chatTemperature;

    @Value("${spring.ai.openai.chat.max-tokens:2000}")
    private Integer chatMaxTokens;

    @Value("${spring.ai.openai.vision.model:gpt-4-vision-preview}")
    private String visionModel;

    @Value("${spring.ai.openai.vision.temperature:0.3}")
    private Double visionTemperature;

    @Value("${spring.ai.openai.vision.max-tokens:2000}")
    private Integer visionMaxTokens;

    /**
     * 创建RestClient.Builder，使用Apache HttpClient以获得更好的兼容性
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        return RestClient.builder()
                .requestFactory(factory);
    }

    /**
     * 创建WebClient.Builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * 创建文本模型ChatClient Bean
     */
    @Bean(name = "textChatClient")
    public ChatClient textChatClient(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        log.info("=== 初始化文本模型ChatClient ===");
        log.info("Base URL: {}", baseUrl);
        log.info("Chat Model: {}", chatModel);

        // 注意：不要手动添加 /v1，OpenAiApi 会自动处理
        log.info("使用 Base URL: {}", baseUrl);

        // 使用包含自定义RestClient的构造函数
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey, restClientBuilder, webClientBuilder);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(chatModel)
                .temperature(chatTemperature)
                .maxTokens(chatMaxTokens)
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
        ChatClient client = ChatClient.builder(chatModel).build();
        log.info("文本模型ChatClient初始化成功");
        return client;
    }

    /**
     * 创建图像模型ChatClient Bean
     */
    @Bean(name = "visionChatClient")
    public ChatClient visionChatClient(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
                                       @Qualifier("visionOpenAiApi") OpenAiApi visionOpenAiApi) {
        log.info("=== 初始化视觉模型ChatClient ===");
        log.info("Base URL: {}", baseUrl);
        log.info("Vision Model: {}", visionModel);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(visionModel)
                .temperature(visionTemperature)
                .maxTokens(visionMaxTokens)
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(visionOpenAiApi, options);
        ChatClient client = ChatClient.builder(chatModel).build();
        log.info("视觉模型ChatClient初始化成功");
        return client;
    }

    /**
     * 创建视觉模型专用的 OpenAiApi Bean
     */
    @Bean(name = "visionOpenAiApi")
    public OpenAiApi visionOpenAiApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        log.info("=== 初始化 Vision OpenAiApi ===");
        log.info("使用 Base URL: {}", baseUrl);
        return new OpenAiApi(baseUrl, apiKey, restClientBuilder, webClientBuilder);
    }

    /**
     * 默认ChatClient（使用文本模型）
     * 保持向后兼容性
     */
    @Primary
    @Bean
    public ChatClient chatClient(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        return textChatClient(restClientBuilder, webClientBuilder);
    }
}
