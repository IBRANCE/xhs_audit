package com.xhs.audit.controller;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xhs.audit.infrastructure.SeleniumManager;
import com.xhs.audit.model.dto.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 健康检查Controller
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private SeleniumManager seleniumManager;

    /**
     * GET /api/v1/audit/health - 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        log.info("健康检查请求");

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", java.time.LocalDateTime.now());

        // 检查各组件状态
        Map<String, Object> components = new HashMap<>();

        // 数据库检查
        components.put("database", checkDatabase());

        // Redis检查
        if (redisConnectionFactory != null) {
            components.put("redis", checkRedis());
        }

        // Selenium检查
        components.put("selenium", checkSelenium());

        health.put("components", components);

        // 判断整体状态
        boolean allHealthy = components.values().stream()
                .allMatch(c -> c instanceof Map && "UP".equals(((Map<?, ?>) c).get("status")));

        if (!allHealthy) {
            health.put("status", "DEGRADED");
        }

        return ResponseEntity.ok(ApiResponse.success(health));
    }

    /**
     * 检查数据库连接
     */
    private Map<String, Object> checkDatabase() {
        Map<String, Object> status = new HashMap<>();
        try {
            Connection conn = dataSource.getConnection();
            String dbName = conn.getMetaData().getDatabaseProductName();
            String version = conn.getMetaData().getDatabaseProductVersion();
            conn.close();

            status.put("status", "UP");
            status.put("details", Map.of(
                    "database", dbName,
                    "version", version));
        } catch (Exception e) {
            log.error("数据库健康检查失败", e);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    /**
     * 检查Redis连接
     */
    private Map<String, Object> checkRedis() {
        Map<String, Object> status = new HashMap<>();
        try {
            redisConnectionFactory.getConnection().ping();
            status.put("status", "UP");
            status.put("details", Map.of("version", "7.x"));
        } catch (Exception e) {
            log.error("Redis健康检查失败", e);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    /**
     * 检查Selenium Grid状态
     */
    private Map<String, Object> checkSelenium() {
        Map<String, Object> status = new HashMap<>();
        try {
            String stats = seleniumManager.getStats();
            status.put("status", "UP");
            status.put("details", Map.of("stats", stats));
        } catch (Exception e) {
            log.error("Selenium健康检查失败", e);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }
}
