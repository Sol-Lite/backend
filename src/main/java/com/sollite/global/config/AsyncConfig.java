package com.sollite.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "orderMatchingExecutor")
    public Executor orderMatchingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("order-match-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "priceCheckExecutor")
    public Executor priceCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("price-check-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("[PRICE_CHECK] 스레드풀 큐 포화로 가격 체크 이벤트 폐기됨 - activeThreads={}, queueSize={}",
                        e.getActiveCount(), e.getQueue().size()));
        executor.initialize();
        return executor;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("[NOTIFICATION] 스레드풀 큐 포화로 알림 이벤트 폐기됨 - activeThreads={}, queueSize={}",
                        e.getActiveCount(), e.getQueue().size()));
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("[ASYNC] 처리되지 않은 예외 - method={}, error={}",
                        method.getName(), ex.getMessage(), ex);
    }
}
