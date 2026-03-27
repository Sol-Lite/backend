package com.sollite.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ExecutorService 빈 등록
 * Spring 종료 시 graceful shutdown을 위해 destroyMethod 지정
 */
@Configuration
public class ExecutorConfig {

    private static final int PRICE_LOOKUP_PARALLELISM = 6;

    @Bean(name = "priceExecutor", destroyMethod = "shutdown")
    public ExecutorService priceExecutor() {
        return Executors.newFixedThreadPool(PRICE_LOOKUP_PARALLELISM,
                r -> {
                    Thread t = new Thread(r, "price-lookup");
                    t.setDaemon(false);
                    return t;
                });
    }
}
