package com.sollite.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class LsBrokerService {

    private final LsTokenService lsTokenService;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${ls.ws.url}")
    private String lsWsUrl;

    private final ReactorNettyWebSocketClient lsClient = new ReactorNettyWebSocketClient();

    // LS 연결 상태
    private Disposable lsConnection;
    private Sinks.Many<String> outboundSink;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // 종목별 구독자 카운팅: "UH1:005930" → 3명
    private final Map<String, AtomicInteger> subscriberCount = new ConcurrentHashMap<>();
    // 현재 LS에 등록된 종목: "UH1:005930"
    private final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();
    // 토픽별 마지막 수신 값: "/topic/stock/trade/005930" → JSON
    private final Map<String, String> lastValues = new ConcurrentHashMap<>();

    // 재연결 스케줄러
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    // TR 코드 → STOMP 토픽 매핑
    private static final Map<String, String> TR_TOPIC_MAP = Map.of(
            "UH1", "/topic/asking/",
            "US3", "/topic/stock/trade/",
            "CUR", "/topic/currency/",
            "GSH", "/topic/foreign/quote/",
            "GSC", "/topic/foreign/transaction/"
    );

    @PostConstruct
    public void init() {
        connectToLs();
    }

    @PreDestroy
    public void destroy() {
        if (lsConnection != null && !lsConnection.isDisposed()) {
            lsConnection.dispose();
        }
        reconnectScheduler.shutdown();
    }

    /**
     * 종목 구독 (클라이언트가 STOMP 토픽 구독 시 호출)
     */
    public void subscribe(String trCd, String key) {
        String subscriptionKey = trCd + ":" + key;
        AtomicInteger count = subscriberCount.computeIfAbsent(subscriptionKey, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        log.info("구독 추가: {} (구독자 수: {})", subscriptionKey, current);

        // 첫 구독자 → LS에 실시간 등록
        if (current == 1 && connected.get()) {
            registerToLs(trCd, key);
        }

        // 마지막 값 즉시 전송 (신규 구독자가 다음 틱까지 기다리지 않도록)
        String topicPrefix = TR_TOPIC_MAP.get(trCd);
        if (topicPrefix != null) {
            String last = lastValues.get(topicPrefix + key);
            if (last != null) {
                messagingTemplate.convertAndSend(topicPrefix + key, last);
            }
        }
    }

    /**
     * 종목 구독 해제 (클라이언트가 STOMP 연결 종료 시 호출)
     */
    public void unsubscribe(String trCd, String key) {
        String subscriptionKey = trCd + ":" + key;
        AtomicInteger count = subscriberCount.get(subscriptionKey);
        if (count == null) return;

        int current = count.decrementAndGet();
        log.info("구독 해제: {} (구독자 수: {})", subscriptionKey, current);

        // 구독자 0 → LS에서 해제
        if (current <= 0) {
            subscriberCount.remove(subscriptionKey);
            if (connected.get()) {
                unregisterFromLs(trCd, key);
            }
            activeSubscriptions.remove(subscriptionKey);
        }
    }

    /**
     * LS WebSocket 연결
     */
    private void connectToLs() {
        outboundSink = Sinks.many().unicast().onBackpressureBuffer();

        lsConnection = lsClient.execute(URI.create(lsWsUrl), lsSession -> {
            connected.set(true);
            reconnectAttempts.set(0);
            log.info("LS WebSocket 연결 성공");

            // 기존 구독 복원
            restoreSubscriptions();

            // 서버 → LS 메시지 전송
            Mono<Void> send = lsSession.send(
                    outboundSink.asFlux().map(lsSession::textMessage)
            );

            // LS → 서버 메시지 수신
            Mono<Void> receive = lsSession.receive()
                    .doOnNext(msg -> log.info("LS 원본 메시지 수신: type={}, payload={}", msg.getType(), msg.getPayloadAsText()))
                    .filter(msg -> msg.getType() == WebSocketMessage.Type.TEXT)
                    .doOnNext(msg -> handleLsMessage(msg.getPayloadAsText()))
                    .then();

            return Mono.zip(send, receive).then();
        }).subscribe(
                null,
                error -> {
                    log.error("LS WebSocket 오류: {}", error.getMessage());
                    connected.set(false);
                    scheduleReconnect();
                },
                () -> {
                    log.info("LS WebSocket 연결 종료");
                    connected.set(false);
                    scheduleReconnect();
                }
        );
    }

    /**
     * LS에 종목 등록 (tr_type: 3)
     */
    private void registerToLs(String trCd, String key) {
        String subscriptionKey = trCd + ":" + key;
        if (activeSubscriptions.contains(subscriptionKey)) return;

        String token = lsTokenService.getAccessToken();
        String trKey = formatTrKey(trCd, key);
        String message = buildLsMessage(token, "3", trCd, trKey);

        log.info("LS 종목 등록 메시지: {}", message);
        outboundSink.tryEmitNext(message);
        activeSubscriptions.add(subscriptionKey);
        log.info("LS 종목 등록: trCd={}, key={}, trKey={}", trCd, key, trKey);
    }

    /**
     * LS에서 종목 해제 (tr_type: 4)
     */
    private void unregisterFromLs(String trCd, String key) {
        String token = lsTokenService.getAccessToken();
        String trKey = formatTrKey(trCd, key);
        String message = buildLsMessage(token, "4", trCd, trKey);

        outboundSink.tryEmitNext(message);
        log.info("LS 종목 해제: trCd={}, key={}", trCd, key);
    }

    /**
     * LS 메시지 수신 → STOMP 토픽으로 broadcast
     */
    private void handleLsMessage(String payload) {
        try {
            log.info("LS 메시지 파싱 시작: {}", payload.length() > 500 ? payload.substring(0, 500) + "..." : payload);
            JsonNode root = objectMapper.readTree(payload);
            JsonNode header = root.get("header");
            if (header == null) {
                log.warn("LS 메시지에 header 없음: {}", payload.length() > 200 ? payload.substring(0, 200) : payload);
                return;
            }

            String trCd = header.get("tr_cd").asText();
            JsonNode body = root.get("body");
            if (body == null) return;

            String topicPrefix = TR_TOPIC_MAP.get(trCd);
            if (topicPrefix == null) {
                log.debug("알 수 없는 tr_cd: {}", trCd);
                return;
            }

            // 종목코드 추출
            String symbol = extractSymbol(trCd, body);
            if (symbol == null) return;

            String topic = topicPrefix + symbol;
            String bodyJson = body.toString();
            lastValues.put(topic, bodyJson);
            messagingTemplate.convertAndSend(topic, bodyJson);

        } catch (Exception e) {
            log.warn("LS 메시지 처리 실패: {}", e.getMessage());
        }
    }

    /**
     * 종목코드 추출 (TR별 필드명이 다름)
     */
    private String extractSymbol(String trCd, JsonNode body) {
        return switch (trCd) {
            case "UH1", "US3" -> body.has("shcode") ? body.get("shcode").asText() : null;
            case "CUR" -> body.has("base_id") ? body.get("base_id").asText() : null;
            case "GSH", "GSC" -> body.has("symbol") ? body.get("symbol").asText() : null;
            default -> null;
        };
    }

    /**
     * TR key 포맷 (해외주식은 18자리 패딩)
     */
    private String formatTrKey(String trCd, String key) {
        if ("GSH".equals(trCd) || "GSC".equals(trCd)) {
            return String.format("%-18s", key).substring(0, 18);
        }
        if ("UH1".equals(trCd) || "US3".equals(trCd)) {
            return String.format("%-10s", "U" + key).substring(0, 10);
        }
        if ("CUR".equals(trCd)) {
            return String.format("%-6s", key).substring(0, 6);
        }
        return key;
    }

    /**
     * LS 메시지 빌드
     */
    private String buildLsMessage(String token, String trType, String trCd, String trKey) {
        return String.format(
                "{\"header\":{\"token\":\"%s\",\"tr_type\":\"%s\"},\"body\":{\"tr_cd\":\"%s\",\"tr_key\":\"%s\"}}",
                token, trType, trCd, trKey
        );
    }

    /**
     * 재연결 스케줄링 (지수 백오프)
     */
    private void scheduleReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            log.error("LS WebSocket 재연결 최대 횟수 초과 ({}회)", MAX_RECONNECT_ATTEMPTS);
            return;
        }

        long delay = (long) Math.pow(2, attempts);
        log.info("LS WebSocket 재연결 예정: {}초 후 (시도 {}/{})", delay, attempts, MAX_RECONNECT_ATTEMPTS);

        reconnectScheduler.schedule(() -> {
            log.info("LS WebSocket 재연결 시도 ({}/{})", attempts, MAX_RECONNECT_ATTEMPTS);
            connectToLs();
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * 재연결 후 기존 구독 복원
     */
    private void restoreSubscriptions() {
        activeSubscriptions.clear();
        for (String subscriptionKey : subscriberCount.keySet()) {
            AtomicInteger count = subscriberCount.get(subscriptionKey);
            if (count != null && count.get() > 0) {
                String[] parts = subscriptionKey.split(":", 2);
                registerToLs(parts[0], parts[1]);
                log.info("구독 복원: {}", subscriptionKey);
            }
        }
    }
}
