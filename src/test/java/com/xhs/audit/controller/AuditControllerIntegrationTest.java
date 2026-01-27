package com.xhs.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.xhs.audit.model.dto.ApiResponse;
import com.xhs.audit.model.dto.AuditRequest;
import com.xhs.audit.model.entity.AuditJob;
import com.xhs.audit.repository.AuditJobRepository;
import com.xhs.audit.repository.AuditResultRepository;

/**
 * AuditController集成测试
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditJobRepository auditJobRepository;

    @Autowired
    private AuditResultRepository auditResultRepository;

    /**
     * 测试获取任务状态
     */
    @Test
    void testGetJobStatus() {
        // 准备测试数据
        AuditJob job = new AuditJob();
        job.setJobId("test-job-123");
        job.setTotalLinks(10);
        job.setCompletedCount(5);
        job.setSuccessCount(3);
        job.setFailedCount(2);
        job.setStatus("PROCESSING");
        job.setFileName("test.xlsx");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        auditJobRepository.save(job);

        // 调用API
        String url = "http://localhost:" + port + "/api/v1/audit/job/test-job-123";
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        // 验证结果
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("000000");

        Map<String, Object> data = response.getBody().getData();
        assertThat(data.get("jobId")).isEqualTo("test-job-123");
        assertThat(data.get("totalLinks")).isEqualTo(10);
        assertThat(data.get("completedCount")).isEqualTo(5);
        assertThat(data.get("status")).isEqualTo("PROCESSING");
    }

    /**
     * 测试验证错误
     */
    @Test
    void testAuditContentWithInvalidUrl() {
        // 准备无效请求
        AuditRequest request = new AuditRequest();
        request.setUrl("http://invalid-url.com");

        // 调用API
        String url = "http://localhost:" + port + "/api/v1/audit/content";
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                });

        // 验证结果（验证失败应返回400）
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isNotEqualTo("000000");
    }

    /**
     * 测试任务不存在
     */
    @Test
    void testGetJobStatusNotFound() {
        // 调用API（不存在的任务ID）
        String url = "http://localhost:" + port + "/api/v1/audit/job/non-existent-job";
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        // 验证结果（应返回404）
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isNotEqualTo("000000");
    }
}
