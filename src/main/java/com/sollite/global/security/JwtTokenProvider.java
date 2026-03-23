package com.sollite.global.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${jwt.auto-login-token-expiry}")
    private long autoLoginTokenExpiry;

    private SecretKey key;

    /**
     * JWT 서명 및 검증에 사용할 HMAC-SHA 키를 초기화한다.
     * 애플리케이션 시작 시 한 번만 실행되어 성능을 최적화한다.
     */
    @PostConstruct
    public void init() {
        // application.yml에서 읽은 비밀키 문자열을 UTF-8 바이트 배열로 변환
        // 그 바이트로부터 HMAC-SHA256 암호화 키를 생성하여 저장
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Access Token을 생성한다.
     *
     * @param userId 사용자 ID
     * @param email 사용자 이메일
     * @return 서명된 JWT Access Token 문자열 (30분 유효)
     */
    public String createAccessToken(Long userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))      // 토큰의 주체(subject)로 userId 설정
                .claim("email", email)                // 추가 정보(claim)로 email 포함
                .issuedAt(now)                        // 발급 시간 설정
                .expiration(new Date(now.getTime() + accessTokenExpiry))  // 만료 시간 설정 (현재 + 30분)
                .signWith(key)                        // HMAC-SHA256으로 서명
                .compact();                           // JWT 문자열로 압축 및 반환
    }

    /**
     * Refresh Token을 생성한다.
     *
     * @param userId    사용자 ID
     * @param expiryMs  토큰 유효시간 (밀리초) — 로그인 시 autoLogin 여부에 따라, 갱신 시 남은 TTL로 전달
     * @return 서명된 JWT Refresh Token 문자열
     */
    public String createRefreshToken(Long userId, long expiryMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(key)
                .compact();
    }

    /**
     * JWT 토큰에서 사용자 ID(subject)를 추출한다.
     *
     * @param token JWT 토큰 문자열
     * @return 토큰에 포함된 사용자 ID
     * @throws JwtException 토큰이 유효하지 않은 경우
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * JWT 토큰의 유효성을 검증한다.
     * 토큰이 만료되었거나 서명이 유효하지 않으면 false를 반환한다.
     *
     * @param token JWT 토큰 문자열
     * @return 유효한 토큰이면 true, 아니면 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Access Token의 유효시간(밀리초)을 반환한다.
     *
     * @return Access Token 유효시간 (기본값: 1800000ms = 30분)
     */
    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    /**
     * Refresh Token의 유효시간(밀리초)을 반환한다.
     *
     * @return Refresh Token 유효시간 (기본값: 604800000ms = 7일)
     */
    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    /**
     * 자동로그인 여부에 따라 Refresh Token 유효시간(밀리초)을 반환한다.
     *
     * @param autoLogin 자동로그인 여부
     * @return 자동로그인 ON: 30일, OFF: 7일
     */
    public long getRefreshTokenExpiry(boolean autoLogin) {
        return autoLogin ? autoLoginTokenExpiry : refreshTokenExpiry;
    }

    /**
     * JWT 토큰을 파싱하고 서명을 검증하여 Claims(클레임)을 추출한다.
     *
     * @param token JWT 토큰 문자열
     * @return 검증된 토큰의 Claims (userId, email 등 포함)
     * @throws JwtException 토큰이 만료되었거나 서명이 유효하지 않은 경우
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)                      // 저장된 key로 서명 검증
                .build()                              // 파서 빌드
                .parseSignedClaims(token)             // JWT 토큰 파싱
                .getPayload();                        // 토큰 내용(클레임) 추출 및 반환
    }
}
