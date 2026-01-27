package com.xhs.audit.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.model.dto.ApiResponse;

/**
 * HealthController集成测试
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testHealthEndpoint() {
        // When: 调用健康检查端点
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "/api/v1/audit/health",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });

        // Then: 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("000000", response.getBody().getCode());
        assertNotNull(response.getBody().getData());

        Map<String, Object> health = response.getBody().getData();
        assertNotNull(health.get("status"));
        assertNotNull(health.get("components"));

        System.out.println("健康检查结果: " + health);
    }
}
