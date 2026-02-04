package com.xhs.audit.config;

import static com.xhs.audit.config.RedisStreamConstants.AUDIT_GROUP;
import static com.xhs.audit.config.RedisStreamConstants.AUDIT_STREAM;
import static com.xhs.audit.config.RedisStreamConstants.CRAWL_GROUP;
import static com.xhs.audit.config.RedisStreamConstants.CRAWL_STREAM;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
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
@EnableScheduling // v4.0: 启用定时任务（Pending 消息回收）
public class AsyncConfig {

    @Autowired
    private StringRedisTemplate redisTemplate;

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
     * v4.0: 扩容到 20 个最大线程
     * 多线程并行处理审核任务
     * 爬取阶段使用crawlExecutor，审核阶段使用此线程池
     */
    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // v4.0: 扩容到 20 个线程以支持更高并发
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20); // v4.0: 从 10 扩容到 20
        executor.setQueueCapacity(2000); // 与爬取队列容量匹配，避免审核任务积压
        executor.setThreadNamePrefix("audit-async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        executor.initialize();
        log.info("审核专用线程池已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={} (v4.0扩容)",
                10, 20, 2000);

        return executor;
    }

    /**
     * v4.0: Redis Stream 消费者线程池
     * 用于执行 Stream 消费者任务
     */
    @Bean(name = "streamConsumerExecutor")
    public ThreadPoolTaskExecutor streamConsumerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("stream-consumer-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("Redis Stream 消费者线程池已初始化: corePoolSize={}, maxPoolSize={}",
                2, 5);

        return executor;
    }

    /**
     * v4.0: 初始化 Redis Stream 消费者组
     */
    @PostConstruct
    public void initRedisStreamGroups() {
        try {
            // 创建爬虫消费者组
            redisTemplate.opsForStream().createGroup(CRAWL_STREAM, ReadOffset.from("0"), CRAWL_GROUP);
            log.info("[RedisStream] 爬虫消费者组已创建: {}", CRAWL_GROUP);
        } catch (Exception e) {
            log.info("[RedisStream] 爬虫消费者组已存在或创建失败: {}", e.getMessage());
        }

        try {
            // 创建审核消费者组
            redisTemplate.opsForStream().createGroup(AUDIT_STREAM, ReadOffset.from("0"), AUDIT_GROUP);
            log.info("[RedisStream] 审核消费者组已创建: {}", AUDIT_GROUP);
        } catch (Exception e) {
            log.info("[RedisStream] 审核消费者组已存在或创建失败: {}", e.getMessage());
        }
    }
}
