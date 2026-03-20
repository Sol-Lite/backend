package com.sollite.websocket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.websocket.dto.AskingResponse;
import com.sollite.websocket.dto.LsAskingRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AskingWebSocketHandler extends TextWebSocketHandler {
    private final LsTokenService lsTokenService;
    private final ObjectMapper objectMapper;

    @Value("${ls.ws.url}")
    private String lsWsUrl;

    private static final String TR_CD = "H1_";
    private static final int MAX_SESSIONS = 100;

    private final ReactorNettyWebSocketClient lsClient = new ReactorNettyWebSocketClient();
    private final Map<String, ConcurrentWebSocketSessionDecorator> frontendSessions = new ConcurrentHashMap<>();
    private final Map<String, Disposable> lsSubscriptions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCount = new AtomicInteger(0);

    // 프론트 /ws/asking/{stockCode} 연결 시
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (sessionCount.get() >= MAX_SESSIONS) {
            log.warn("최대 세션 수 초과: sessionId={}", session.getId());
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }

        String sessionId = session.getId();
        String stockCode = extractStockCode(session);

        ConcurrentWebSocketSessionDecorator safeSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
        frontendSessions.put(sessionId, safeSession);
        sessionCount.incrementAndGet();

        log.info("프론트 웹소켓 연결: sessionId={}, stockCode={}", sessionId, stockCode);
        connectToLs(sessionId, stockCode, safeSession);
    }

    // 프론트 연결 종료 시
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();

        // LS 구독 해제
        Disposable sub = lsSubscriptions.remove(sessionId);
        if (sub != null && !sub.isDisposed()) {
            sub.dispose();
        }
        frontendSessions.remove(sessionId);
        sessionCount.decrementAndGet();
        log.info("프론트 웹소켓 종료: sessionId={}, status={}", sessionId, status);
    }

    // 전송 오류 발생 시
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("웹소켓 전송 오류: sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    // LS WebSocket 연결 + 실시간 데이터 수신
    private void connectToLs(String sessionId, String stockCode, ConcurrentWebSocketSessionDecorator frontendSession) {
        String token = lsTokenService.getAccessToken();

        Disposable subscription = lsClient.execute(URI.create(lsWsUrl), lsSession -> {
            String subMsg = buildMessage(token, "3", stockCode);
            Mono<Void> send = lsSession.send(
                    Mono.just(lsSession.textMessage(subMsg))
            );

            Mono<Void> receive = lsSession.receive()
                    .filter(msg -> msg.getType() == WebSocketMessage.Type.TEXT)
                    .doOnNext(msg -> relay(frontendSession, msg.getPayloadAsText(), stockCode))
                    .then();

            return send.then(receive);
        }).subscribe(
                null,
                error -> log.error("LS 웹소켓 오류: sessionId={}, error={}", sessionId, error.getMessage()),
                () -> log.info("LS 웹소켓 연결 종료: sessionId={}", sessionId)
        );
        lsSubscriptions.put(sessionId, subscription);
    }

    private void relay(ConcurrentWebSocketSessionDecorator frontendSession, String lsPayload, String stockCode) {
        try {
            LsAskingRes lsRes = objectMapper.readValue(lsPayload, LsAskingRes.class);
            LsAskingRes.LsAskingBody body = lsRes.body();
            if (body == null) return;

            AskingResponse response = new AskingResponse(
                    stockCode,
                    body.hotime(),
                    body.parsedTotOfferRem(),
                    body.parsedTotBidRem(),
                    body.toAsks(),
                    body.toBids()
            );

            String json = objectMapper.writeValueAsString(response);

            if (frontendSession.isOpen()) {
                frontendSession.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("LS 메시지 처리 실패: {}", e.getMessage());
        }
    }

    // LS WebSocket 구독/해제 메시지
    private String buildMessage(String token, String trType, String stockCode) {
        return String.format(
                "{\"header\":{\"token\":\"%s\",\"tr_type\":\"%s\"},\"body\":{\"tr_cd\":\"%s\",\"tr_key\":\"%s\"}}",
                token, trType, TR_CD, stockCode
        );
    }

    private String extractStockCode(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
