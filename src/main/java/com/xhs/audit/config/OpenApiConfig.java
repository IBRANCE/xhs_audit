package com.xhs.audit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI配置
 * 提供Swagger UI界面用于API测试
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("小红书审核系统 API")
                        .version("1.0.0")
                        .description("""
                                小红书内容审核系统REST API文档
                                
                                ## 功能特性
                                - 单条内容审核
                                - 批量内容审核
                                - Excel文件上传/下载
                                - 任务状态查询
                                - 审核结果查询
                                
                                ## 认证方式
                                当前版本无需认证
                                
                                ## 使用说明
                                1. 使用 `/api/v1/audit/content` 进行单条审核
                                2. 使用 `/api/v1/file/upload` 批量上传Excel文件
                                3. 使用 `/api/v1/audit/job/{jobId}` 查询任务进度
                                4. 使用 `/api/v1/file/download/{jobId}` 下载审核结果
                                """)
                        .contact(new Contact()
                                .name("XHS Audit Team")
                                .email("support@xhsaudit.com")
                                .url("https://github.com/your-repo/xhs-audit"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("本地开发服务器"),
                        new Server()
                                .url("https://api.xhsaudit.com")
                                .description("生产环境服务器")));
    }
}
