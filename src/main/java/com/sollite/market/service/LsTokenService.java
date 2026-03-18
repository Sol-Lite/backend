package com.sollite.market.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.market.exception.MarketErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LsTokenService {
    private final WebClient lsWebClient;

    private String cachedToken;
    private Instant tokenExpiresAt;

    @Value("${ls.api.appkey}") private String appKey;
    @Value("${ls.api.appsecret}") private String appSecret;

    public synchronized String getAccessToken() {
        if (cachedToken == null || Instant.now().isAfter(tokenExpiresAt.minusSeconds(60))) {
            issueNewToken();
        }
        return cachedToken;
    }

    public synchronized void invalidateToken() {
        log.info("토큰 강제 무효화");
        cachedToken = null;
        tokenExpiresAt = null;
    }

    private void issueNewToken() {
        log.info("토큰 발급 요청");

        Map<String, String> response = lsWebClient.post()
                .uri("/oauth2/token")
                .header("content-type", "application/x-www-form-urlencoded")
                .bodyValue("grant_type=client_credentials&appkey=" + appKey + "&appsecretkey=" + appSecret + "&scope=oob")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .block();

        if (response != null && response.containsKey("access_token")) {
            cachedToken = response.get("access_token");
            long expiresIn = Long.parseLong(response.getOrDefault("expires_in", "86400"));
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            log.info("새 토큰 발급 완료, 만료까지 {}초", expiresIn);
            return;
        }
        throw new BusinessException(MarketErrorCode.LS_TOKEN_FETCH_FAILED);
    }
}
