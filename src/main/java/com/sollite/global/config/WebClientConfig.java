package com.sollite.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Value("${ls.api.url}")
    private String baseUrl;

    @Value("${ls.api.appkey}")
    private String appKey;

    @Value("${ls.api.appsecret}")
    private String appSecret;

    @Bean
    public WebClient lsWebClient()
    {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("appkey", appKey)
                .defaultHeader("appsecret", appSecret)
                .build();
    }
}
