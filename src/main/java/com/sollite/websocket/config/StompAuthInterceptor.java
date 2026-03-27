package com.sollite.websocket.config;

import com.sollite.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP 채널 인터셉터.
 *
 * CONNECT: Authorization 헤더의 JWT를 검증하여 StompPrincipal(userId)을 세션에 바인딩.
 * SUBSCRIBE: /topic/notifications/{userId} 구독 시 자신의 userId만 구독 가능하도록 검증.
 *
 * 인증 실패 시 예외를 던져 연결/구독을 차단한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(message, accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(message, accessor);
        }

        return message;
    }

    private void handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("[STOMP] CONNECT: Authorization 헤더 없음 — 비인증 연결 허용");
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("[STOMP] CONNECT: 유효하지 않은 JWT");
            throw new MessageDeliveryException(message, "유효하지 않은 토큰입니다");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        accessor.setUser(new StompPrincipal(String.valueOf(userId)));
        log.debug("[STOMP] CONNECT: userId={} 인증 완료", userId);
    }

    private void handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(NOTIFICATION_TOPIC_PREFIX)) {
            return; // 알림 토픽이 아니면 검증 생략
        }

        Principal principal = accessor.getUser();
        String subscribedUserId = destination.substring(NOTIFICATION_TOPIC_PREFIX.length());

        if (principal == null || !subscribedUserId.equals(principal.getName())) {
            log.warn("[STOMP] SUBSCRIBE: 알림 구독 권한 없음 - destination={}, principal={}",
                    destination, principal != null ? principal.getName() : "null");
            throw new MessageDeliveryException(message, "해당 알림 채널에 구독 권한이 없습니다");
        }
    }
}
