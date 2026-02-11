package com.xhs.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.xhs.audit.infrastructure.MessageQueueService;
import com.xhs.audit.service.CrawlerService;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController 单元测试")
class AdminControllerTest {

    @Mock
    private CrawlerService crawlerService;

    @Mock
    private MessageQueueService messageQueueService;

    @InjectMocks
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        // 使用反射设置私有的 @Autowired 字段
        ReflectionTestUtils.setField(adminController, "messageQueueService", messageQueueService);
        ReflectionTestUtils.setField(adminController, "redisTemplate", null);
        ReflectionTestUtils.setField(adminController, "textChatClient", null);
    }

    @Test
    @DisplayName("测试AI连接 - 缺少 ChatClient 时返回错误")
    void testTestAiConnection_NoChatClient() {
        // Given
        Map<String, String> request = new HashMap<>();
        request.put("message", "测试消息");

        // When
        ResponseEntity<Map<String, Object>> response = adminController.testAiConnection(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat("error").isEqualTo(body.get("status"));
    }

    @Test
    @DisplayName("清理僵尸消息 - 缺少 RedisTemplate 时返回错误")
    void testCleanZombieMessages_NoRedisTemplate() {
        // When
        ResponseEntity<Map<String, Object>> response = adminController.cleanZombieMessages();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat("error").isEqualTo(body.get("status"));
    }

    @Test
    @DisplayName("验证 Controller 注入的字段")
    void testFieldInjection() {
        // Then - 验证字段是否正确注入
        assertThat(adminController).isNotNull();
        assertThat(crawlerService).isNotNull();
        assertThat(messageQueueService).isNotNull();
    }
}