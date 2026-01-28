package com.xhs.audit.config;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
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

    @Value("${spring.ai.openai.timeout:120}")
    private Integer timeoutSeconds;

    /**
     * 创建带超时配置的RestClient.Builder
     */
    private RestClient.Builder createRestClientBuilder() {
        // 创建带超时配置的HttpClient
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 使用HttpClient创建RequestFactory
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    /**
     * 创建WebClient.Builder
     */
    private WebClient.Builder createWebClientBuilder() {
        return WebClient.builder();
    }

    /**
     * 创建默认的ResponseErrorHandler
     */
    private ResponseErrorHandler createResponseErrorHandler() {
        return new org.springframework.web.client.DefaultResponseErrorHandler();
    }

    /**
     * 创建文本模型ChatClient Bean
     * 用于文本审核、摘要等任务
     */
    @Bean(name = "textChatClient")
    public ChatClient textChatClient() {
        log.info("=== 初始化文本模型ChatClient ===");
        log.info("Base URL: {}", baseUrl);
        log.info("Chat Model: {}", chatModel);
        log.info("Temperature: {}", chatTemperature);
        log.info("Max Tokens: {}", chatMaxTokens);
        log.info("Timeout: {}s", timeoutSeconds);

        // 确保base-url包含/v1路径（OpenAI兼容API标准）
        String fullBaseUrl = baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
        log.info("使用完整Base URL: {}", fullBaseUrl);

        // Spring AI 1.0.0-M5 构造函数签名 - 需要提供所有必需的参数
        OpenAiApi openAiApi = new OpenAiApi(fullBaseUrl, apiKey,
                createRestClientBuilder(),
                createWebClientBuilder(),
                createResponseErrorHandler());

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
     * 用于图像识别、OCR等视觉任务
     */
    @Bean(name = "visionChatClient")
    public ChatClient visionChatClient() {
        log.info("=== 初始化视觉模型ChatClient ===");
        log.info("Base URL: {}", baseUrl);
        log.info("Vision Model: {}", visionModel);
        log.info("Temperature: {}", visionTemperature);
        log.info("Max Tokens: {}", visionMaxTokens);

        // 确保base-url包含/v1路径（OpenAI兼容API标准）
        String fullBaseUrl = baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
        log.info("使用完整Base URL: {}", fullBaseUrl);

        // Spring AI 1.0.0-M5 构造函数签名 - 需要提供所有必需的参数
        OpenAiApi openAiApi = new OpenAiApi(fullBaseUrl, apiKey,
                createRestClientBuilder(),
                createWebClientBuilder(),
                createResponseErrorHandler());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(visionModel)
                .temperature(visionTemperature)
                .maxTokens(visionMaxTokens)
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
        ChatClient client = ChatClient.builder(chatModel).build();
        log.info("视觉模型ChatClient初始化成功");
        return client;
    }

    /**
     * 默认ChatClient（使用文本模型）
     * 保持向后兼容性
     */
    @Primary
    @Bean
    public ChatClient chatClient() {
        return textChatClient();
    }
}
