package com.sollite.global.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.global.exception.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Slf4j
@Service
public class KisTokenService {

    private final WebClient kisWebClient;

    @Value("${kis.api.appkey}") private String appKey;
    @Value("${kis.api.appsecret}") private String appSecret;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public KisTokenService(@Qualifier("kisWebClient") WebClient kisWebClient) {
        this.kisWebClient = kisWebClient;
    }

    public synchronized String getAccessToken() {
        if (cachedToken == null || tokenExpiresAt == null || Instant.now().isAfter(tokenExpiresAt.minusSeconds(300))) {
            issueNewToken();
        }
        return cachedToken;
    }

    public synchronized void invalidateToken() {
        log.info("KIS 토큰 강제 무효화");
        cachedToken = null;
        tokenExpiresAt = null;
    }

    private void issueNewToken() {
        log.info("KIS 토큰 발급 요청");

        record TokenRequest(String grant_type, String appkey, String appsecret) {}
        record TokenResponse(String access_token, long expires_in) {}

        TokenResponse response = kisWebClient.post()
                .uri("/oauth2/tokenP")
                .header("content-type", "application/json; charset=UTF-8")
                .bodyValue(new TokenRequest("client_credentials", appKey, appSecret))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();

        if (response == null || response.access_token() == null) {
            throw new BusinessException(GlobalErrorCode.KIS_TOKEN_FETCH_FAILED);
        }

        cachedToken = response.access_token();
        tokenExpiresAt = Instant.now().plusSeconds(response.expires_in());
        log.info("KIS 토큰 발급 완료, 만료까지 {}초", response.expires_in());
    }
}
