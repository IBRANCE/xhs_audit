package com.xhs.audit;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;

import java.util.List;

/**
 * 调试LLM连接问题的简单测试
 */
public class LlmDebugTest {

    public static void main(String[] args) {
        String baseUrl = "http://localhost:1234/v1";
        String apiKey = "sk-local-test";
        String model = "qwen/qwen3-4b";

        System.out.println("=== LLM连接调试 ===");
        System.out.println("Base URL: " + baseUrl);
        System.out.println("Model: " + model);

        try {
            // 1. 直接创建OpenAiApi
            System.out.println("\n1. 创建OpenAiApi...");
            OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);
            System.out.println("   OpenAiApi创建成功");

            // 2. 创建ChatModel
            System.out.println("\n2. 创建OpenAiChatModel...");
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(0.7)
                    .build();
            OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
            System.out.println("   OpenAiChatModel创建成功");

            // 3. 尝试直接调用API
            System.out.println("\n3. 尝试直接调用chatCompletionEntity...");
            ChatCompletionMessage msg = new ChatCompletionMessage("Hello, this is a test", Role.USER);
            ChatCompletionRequest request = new ChatCompletionRequest(
                    List.of(msg),  // messages
                    model,         // model
                    null           // 其他参数使用默认值
            );

            ResponseEntity<ChatCompletion> response = openAiApi.chatCompletionEntity(request);
            System.out.println("   响应状态: " + response.getStatusCode());
            System.out.println("   响应体: " + response.getBody());

        } catch (Exception e) {
            System.out.println("\n!!! 发生异常 !!!");
            System.out.println("异常类型: " + e.getClass().getName());
            System.out.println("异常信息: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
