package com.xhs.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 小红书内容审核Agent系统启动类
 */
@SpringBootApplication
@EnableAsync
@EnableCaching
public class XhsAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(XhsAuditApplication.class, args);
    }

}
