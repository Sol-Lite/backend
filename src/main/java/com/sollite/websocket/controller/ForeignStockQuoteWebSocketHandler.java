package com.sollite.websocket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.websocket.dto.LsGshRes;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForeignStockQuoteWebSocketHandler extends TextWebSocketHandler {
    private final LsTokenService lsTokenService;
    private final ObjectMapper objectMapper;

    @Value("${ls.ws.url}")
    private String lsWsUrl;

    private static final String TR_CD = "GSH";
    private static final int MAX_SESSIONS = 100;

    private final ReactorNettyWebSocketClient lsClient = new ReactorNettyWebSocketClient();
    private final Map<String, ConcurrentWebSocketSessionDecorator> frontendSessions = new ConcurrentHashMap<>();
    private final Map<String, Disposable> lsSubscriptions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCount = new AtomicInteger(0);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (sessionCount.get() >= MAX_SESSIONS) {
            log.warn("해외주식 최대 세션 수 초과: sessionId={}", session.getId());
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }

        String sessionId = session.getId();
        String symbol = extractSymbol(session);

        ConcurrentWebSocketSessionDecorator safeSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
        frontendSessions.put(sessionId, safeSession);
        sessionCount.incrementAndGet();

        log.info("해외주식 호가 웹소켓 연결: sessionId={}, symbol={}", sessionId, symbol);
        connectToLs(sessionId, symbol, safeSession);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();

        Disposable sub = lsSubscriptions.remove(sessionId);
        if (sub != null && !sub.isDisposed()) {
            sub.dispose();
        }
        frontendSessions.remove(sessionId);
        sessionCount.decrementAndGet();
        log.info("해외주식 호가 웹소켓 종료: sessionId={}, status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("해외주식 호가 웹소켓 전송 오류: sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    private void connectToLs(String sessionId, String symbol, ConcurrentWebSocketSessionDecorator frontendSession) {
        String token = lsTokenService.getAccessToken();

        Disposable subscription = lsClient.execute(URI.create(lsWsUrl), lsSession -> {
            String subMsg = buildMessage(token, "3", symbol);
            Mono<Void> send = lsSession.send(
                    Mono.just(lsSession.textMessage(subMsg))
            );

            Mono<Void> receive = lsSession.receive()
                    .filter(msg -> msg.getType() == WebSocketMessage.Type.TEXT)
                    .doOnNext(msg -> relay(frontendSession, msg.getPayloadAsText(), symbol))
                    .then();

            return send.then(receive);
        }).subscribe(
                null,
                error -> log.error("LS 호가 웹소켓 오류: sessionId={}, error={}", sessionId, error.getMessage()),
                () -> log.info("LS 호가 웹소켓 연결 종료: sessionId={}", sessionId)
        );
        lsSubscriptions.put(sessionId, subscription);
    }

    private void relay(ConcurrentWebSocketSessionDecorator frontendSession, String lsPayload, String symbol) {
        try {
            LsGshRes lsRes = objectMapper.readValue(lsPayload, LsGshRes.class);
            LsGshRes.LsGshBody body = lsRes.body();
            if (body == null) return;

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("loctime", body.loctime());
            response.put("kortime", body.kortime());
            response.put("asks", body.toAsks());
            response.put("bids", body.toBids());
            response.put("totOfferCnt", body.totoffercnt());
            response.put("totBidCnt", body.totbidcnt());
            response.put("totOfferRem", body.totofferrem());
            response.put("totBidRem", body.totbidrem());

            String json = objectMapper.writeValueAsString(response);

            if (frontendSession.isOpen()) {
                frontendSession.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("LS 호가 메시지 처리 실패: {}", e.getMessage());
        }
    }

    private String buildMessage(String token, String trType, String symbol) {
        // 해외주식 심볼을 18자리로 패딩
        String paddedSymbol = String.format("%-18s", symbol).substring(0, 18);
        return String.format(
                "{\"header\":{\"token\":\"%s\",\"tr_type\":\"%s\"},\"body\":{\"tr_cd\":\"%s\",\"tr_key\":\"%s\"}}",
                token, trType, TR_CD, paddedSymbol
        );
    }

    private String extractSymbol(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
