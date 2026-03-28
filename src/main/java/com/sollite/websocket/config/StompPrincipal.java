package com.sollite.websocket.config;

import java.security.Principal;

/**
 * STOMP CONNECT 시 JWT에서 추출한 userId를 담는 Principal 구현체.
 * StompAuthInterceptor가 생성하여 세션에 바인딩하고,
 * SUBSCRIBE 단계에서 구독 목적지 검증에 사용된다.
 */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}

