package com.sollite.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Value("${ls.api.url}")
    private String baseUrl;

    @Value("${ls.api.appkey}")
    private String appKey;

    @Value("${ls.api.appsecret}")
    private String appSecret;

    @Value("${kis.api.url}")
    private String kisBaseUrl;

    @Value("${kis.api.appkey}")
    private String kisAppKey;

    @Value("${kis.api.appsecret}")
    private String kisAppSecret;

    @Bean
    public WebClient lsWebClient()
    {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("appkey", appKey)
                .defaultHeader("appsecret", appSecret)
                .build();
    }

    @Bean
    public WebClient kisWebClient() {
        return WebClient.builder()
                .baseUrl(kisBaseUrl)
                .defaultHeader("appkey", kisAppKey)
                .defaultHeader("appsecret", kisAppSecret)
                .build();
    }

    @Bean
    public WebClient yahooWebClient() {
        return WebClient.builder()
                .baseUrl("https://query1.finance.yahoo.com")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
    }
}
