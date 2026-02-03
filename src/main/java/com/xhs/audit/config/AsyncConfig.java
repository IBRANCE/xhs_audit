package com.xhs.audit.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 异步任务配置
 * 配置线程池用于异步处理批量审核任务
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 异步任务线程池
     * 
     * 参数说明:
     * - 核心线程数: 10 (始终保活的线程数)
     * - 最大线程数: 50 (核心线程满后最多再创建40个)
     * - 队列容量: 1000 (缓冲待处理的任务)
     * - 拒绝策略: CallerRunsPolicy (队列满时由调用线程执行)
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(10);

        // 最大线程数
        executor.setMaxPoolSize(50);

        // 队列容量
        executor.setQueueCapacity(1000);

        // 线程名前缀
        executor.setThreadNamePrefix("audit-executor-");

        // 空闲线程存活时间（秒）
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：队列满时由调用线程执行（避免丢弃任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 最多等待300秒
        executor.setAwaitTerminationSeconds(300);

        // 初始化
        executor.initialize();

        log.info("异步任务线程池已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                10, 50, 1000);

        return executor;
    }

    /**
     * 爬取专用线程池
     * ✅ Selenium 支持多线程并发 - 配置为8个线程，匹配 SeleniumManager 的 poolSize
     * 相比 Playwright，Selenium 的 RemoteWebDriver 通过 HTTP 与 Grid 通信，完全支持并发
     */
    @Bean(name = "crawlExecutor")
    public Executor crawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 多线程并发爬取，匹配 Selenium Grid 的浏览器实例数（从4扩容到8）
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000); // 增大队列容量以缓冲所有待爬取任务
        executor.setThreadNamePrefix("crawl-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        executor.initialize();
        log.info("爬取专用线程池已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={} (Selenium多线程并发)",
                8, 8, 2000);

        return executor;
    }

    /**
     * 审核专用线程池
     * 多线程并行处理审核任务
     * 爬取阶段使用crawlExecutor，审核阶段使用此线程池
     */
    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 多线程审核，支持并行处理
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(2000); // 与爬取队列容量匹配，避免审核任务积压
        executor.setThreadNamePrefix("audit-async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        executor.initialize();
        log.info("审核专用线程池已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={} (多线程并行审核)",
                5, 10, 2000);

        return executor;
    }
}
