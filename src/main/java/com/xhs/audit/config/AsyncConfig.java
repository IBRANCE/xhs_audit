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
     * 审核专用线程池
     * 用于批量审核任务的并行处理
     * 线程池大小 = 浏览器池大小(3)，保证最多3个并发爬取任务
     */
    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 线程池大小设为1，保证串行爬取，避免CDP冲突
        // 每个Browser实例独立使用，串行执行CDP操作
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        executor.initialize();
        log.info("审核专用线程池已初始化: corePoolSize={}, maxPoolSize={} (串行执行，避免CDP冲突)",
                1, 1);

        return executor;
    }
}
