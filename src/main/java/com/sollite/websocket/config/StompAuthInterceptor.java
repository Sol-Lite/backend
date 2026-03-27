package com.sollite.websocket.config;

import com.sollite.global.exception.BusinessException;
import com.sollite.global.security.JwtTokenProvider;
import com.sollite.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * WebSocket STOMP 연결 시 JWT 토큰 검증
 * CONNECT 프레임에 Authorization 헤더가 있는지 확인하고 토큰 유효성 검증
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // CONNECT 커맨드만 검증
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader(AUTH_HEADER);
            if (authHeader == null || authHeader.isBlank()) {
                log.warn("[WebSocket] Authorization 헤더 누락");
                throw new BusinessException(UserErrorCode.INVALID_TOKEN);
            }

            String token = authHeader.startsWith(BEARER_PREFIX)
                    ? authHeader.substring(BEARER_PREFIX.length())
                    : authHeader;

            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("[WebSocket] 토큰 유효성 검증 실패");
                throw new BusinessException(UserErrorCode.INVALID_TOKEN);
            }

            // 토큰에서 userId 추출하여 accessor에 저장
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            accessor.addNativeHeader("X-User-Id", userId.toString());
        }

        return message;
    }
}
