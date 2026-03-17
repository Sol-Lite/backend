package com.sollite.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LsTokenService {
    private final WebClient lsWebClient;
    private String cachedToken;

    @Value("${ls.api.appkey}") private String appKey;
    @Value("${ls.api.appsecret}") private String appSecret;

    public String getAccessToken() {
        if (cachedToken != null) {
            return cachedToken;
        }

        log.info("토큰 발급 요청");

        Map<String, String> response = lsWebClient.post()
                .uri("/oauth2/token")
                .header("content-type", "application/x-www-form-urlencoded")
                .bodyValue("grant_type=client_credentials&appkey=" + appKey + "&appsecretkey=" + appSecret + "&scope=oob")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .block();
        if (response != null && response.containsKey("access_token"))
        {
            cachedToken = response.get("access_token");
            log.info("새 토큰 발급 완료");
            return cachedToken;
        }
        throw new RuntimeException("토큰 발급 실패");
    }
}