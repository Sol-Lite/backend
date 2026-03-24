package com.sollite.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.order.event.MarketTickEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Locale;
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
    private final StringRedisTemplate redisTemplate;
    private final InstrumentRepository instrumentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final String INDEX_SNAPSHOT_PREFIX = "index:snapshot:";
    private static final Duration INDEX_SNAPSHOT_TTL = Duration.ofHours(48);
    private static final String CURRENCY_SNAPSHOT_PREFIX = "currency:snapshot:";
    private static final Duration CURRENCY_SNAPSHOT_TTL = Duration.ofHours(48);

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
            "GSC", "/topic/foreign/transaction/",
            "IJ_", "/topic/index/domestic/",
            "MK2", "/topic/index/foreign/"
    );

    // 항상 유지할 영구 구독 (지수)
    private static final Set<String> PERMANENT_SUBSCRIPTIONS = Set.of(
            "IJ_:001",
            "IJ_:301",
            "MK2:SPI@SPX",
            "MK2:NAS@IXIC",
            "CUR:USD"
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

        log.info("[SUBSCRIBE] trCd={}, key={}, 구독자수={}, LS연결상태={}", trCd, key, current, connected.get());

        // 첫 구독자 → LS에 실시간 등록
        if (current == 1) {
            if (connected.get()) {
                log.info("[SUBSCRIBE] 첫 구독자 → LS 등록 시작: {}", subscriptionKey);
                registerToLs(trCd, key);
            } else {
                log.warn("[SUBSCRIBE] 첫 구독자이나 LS 미연결 상태 → 등록 보류: {}", subscriptionKey);
            }
        } else {
            log.info("[SUBSCRIBE] 추가 구독자 → LS 등록 스킵 (이미 등록됨): {}", subscriptionKey);
        }

        // 마지막 값 즉시 전송 (신규 구독자가 다음 틱까지 기다리지 않도록)
        String topicPrefix = TR_TOPIC_MAP.get(trCd);
        if (topicPrefix != null) {
            String last = lastValues.get(topicPrefix + key);
            if (last != null) {
                log.info("[SUBSCRIBE] lastValue 즉시 전송: topic={}", topicPrefix + key);
                messagingTemplate.convertAndSend(topicPrefix + key, last);
            } else {
                log.info("[SUBSCRIBE] lastValue 없음 (첫 수신 대기): topic={}", topicPrefix + key);
            }
        }
    }

    /**
     * 종목 구독 해제 (클라이언트가 STOMP 연결 종료 시 호출)
     */
    public void unsubscribe(String trCd, String key) {
        String subscriptionKey = trCd + ":" + key;
        AtomicInteger count = subscriberCount.get(subscriptionKey);
        if (count == null) {
            log.warn("[UNSUBSCRIBE] 카운터 없음 (이미 해제됨?): {}", subscriptionKey);
            return;
        }

        int current = count.decrementAndGet();
        log.info("[UNSUBSCRIBE] trCd={}, key={}, 남은구독자수={}", trCd, key, current);

        // 구독자 0 → LS에서 해제
        if (current <= 0) {
            subscriberCount.remove(subscriptionKey);
            if (connected.get()) {
                log.info("[UNSUBSCRIBE] 구독자 0 → LS 해제: {}", subscriptionKey);
                unregisterFromLs(trCd, key);
            } else {
                log.warn("[UNSUBSCRIBE] 구독자 0이나 LS 미연결 → 해제 메시지 스킵: {}", subscriptionKey);
            }
            activeSubscriptions.remove(subscriptionKey);
        }
    }

    /**
     * LS WebSocket 연결
     */
    private void connectToLs() {
        String restoreToken = lsTokenService.getAccessToken();
        outboundSink = Sinks.many().unicast().onBackpressureBuffer();

        log.info("[LS-WS] 연결 시도: url={}", lsWsUrl);
        lsConnection = lsClient.execute(URI.create(lsWsUrl), lsSession -> {
            connected.set(true);
            reconnectAttempts.set(0);
            log.info("[LS-WS] 연결 성공. 기존 구독 복원 시작");

            // 기존 구독 복원
            restoreSubscriptions(restoreToken);

            // 서버 → LS 메시지 전송
            Mono<Void> send = lsSession.send(
                    outboundSink.asFlux()
                            .doOnNext(msg -> log.info("[LS-WS→] 전송: {}", msg))
                            .map(lsSession::textMessage)
            );

            // LS → 서버 메시지 수신
            Mono<Void> receive = lsSession.receive()
                    .filter(msg -> msg.getType() == WebSocketMessage.Type.TEXT)
                    .doOnNext(msg -> {
                        String payload = msg.getPayloadAsText();
                        log.info("[LS-WS←] 수신: {}", payload.length() > 300 ? payload.substring(0, 300) + "..." : payload);
                        handleLsMessage(payload);
                    })
                    .then();

            return Mono.zip(send, receive).then();
        }).subscribe(
                null,
                error -> {
                    log.error("[LS-WS] 연결 오류 (연결끊김): {}", error.getMessage());
                    connected.set(false);
                    scheduleReconnect();
                },
                () -> {
                    log.warn("[LS-WS] 서버에 의해 연결 종료됨");
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
        if (activeSubscriptions.contains(subscriptionKey)) {
            log.info("[LS-REG] 이미 등록된 구독 스킵: {}", subscriptionKey);
            return;
        }

        String token = lsTokenService.getAccessToken();
        String trKey = formatTrKey(trCd, key);
        String message = buildLsMessage(token, "3", trCd, trKey);

        Sinks.EmitResult result = outboundSink.tryEmitNext(message);
        activeSubscriptions.add(subscriptionKey);
        log.info("[LS-REG] 등록 완료: trCd={}, key={}, trKey='{}', emitResult={}", trCd, key, trKey, result);
    }

    /**
     * LS에서 종목 해제 (tr_type: 4)
     */
    private void unregisterFromLs(String trCd, String key) {
        String token = lsTokenService.getAccessToken();
        String trKey = formatTrKey(trCd, key);
        String message = buildLsMessage(token, "4", trCd, trKey);

        Sinks.EmitResult result = outboundSink.tryEmitNext(message);
        log.info("[LS-UNREG] 해제 완료: trCd={}, key={}, trKey='{}', emitResult={}", trCd, key, trKey, result);
    }

    /**
     * LS 메시지 수신 → STOMP 토픽으로 broadcast
     */
    private void handleLsMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode header = root.get("header");
            if (header == null) {
                log.warn("[LS-MSG] header 없음: {}", payload.length() > 200 ? payload.substring(0, 200) : payload);
                return;
            }

            String trCd = header.get("tr_cd").asText();
            String rspCd = header.has("rsp_cd") ? header.get("rsp_cd").asText() : null;
            String rspMsg = header.has("rsp_msg") ? header.get("rsp_msg").asText() : null;
            String trType = header.has("tr_type") ? header.get("tr_type").asText() : null;

            // 구독/해제 응답 (body=null인 ACK 메시지)
            JsonNode body = root.get("body");
            if (body == null || body.isNull()) {
                log.info("[LS-MSG] ACK 수신: trCd={}, trType={}, rspCd={}, rspMsg={}", trCd, trType, rspCd, rspMsg);
                return;
            }

            String topicPrefix = TR_TOPIC_MAP.get(trCd);
            if (topicPrefix == null) {
                log.warn("[LS-MSG] 매핑 없는 tr_cd: {}", trCd);
                return;
            }

            // 종목코드 추출
            String symbol = extractSymbol(trCd, body);
            if (symbol == null) {
                log.warn("[LS-MSG] symbol 추출 실패: trCd={}, body={}", trCd, body);
                return;
            }

            String topic = topicPrefix + symbol;
            String bodyJson = body.toString();
            lastValues.put(topic, bodyJson);
            messagingTemplate.convertAndSend(topic, bodyJson);
            log.info("[LS-MSG] STOMP 브로드캐스트: topic={}", topic);

            // 지수 데이터는 Redis에 스냅샷 저장 (장 마감 후에도 종가 제공)
            if (topic.startsWith("/topic/index/")) {
                redisTemplate.opsForValue().set(INDEX_SNAPSHOT_PREFIX + topic, bodyJson, INDEX_SNAPSHOT_TTL);
            }
            if (topic.startsWith("/topic/currency/")) {
                redisTemplate.opsForValue().set(CURRENCY_SNAPSHOT_PREFIX + topic, bodyJson, CURRENCY_SNAPSHOT_TTL);
            }

            // 체결 틱(US3/GSC)인 경우 주문 매칭용 내부 이벤트 발행
            if ("US3".equals(trCd) || "GSC".equals(trCd)) {
                java.math.BigDecimal tickPrice = extractTradePrice(trCd, body);
                if (tickPrice != null) {
                    applicationEventPublisher.publishEvent(
                            new MarketTickEvent(symbol, tickPrice, trCd, java.time.Instant.now()));
                }
            }

        } catch (Exception e) {
            log.error("[LS-MSG] 처리 예외: {}, payload={}", e.getMessage(),
                    payload.length() > 200 ? payload.substring(0, 200) : payload, e);
        }
    }

    /**
     * 체결가 추출 (주문 매칭용)
     * US3(국내체결): price 필드
     * GSC(해외체결): price 필드
     */
    private java.math.BigDecimal extractTradePrice(String trCd, JsonNode body) {
        try {
            String priceField = "price";
            if (body.has(priceField)) {
                String raw = body.get(priceField).asText().replace(",", "").trim();
                if (!raw.isEmpty()) {
                    return new java.math.BigDecimal(raw);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("[LS-MSG] 체결가 파싱 실패: trCd={}, body={}", trCd, body);
        }
        return null;
    }

    /**
     * 종목코드 추출 (TR별 필드명이 다름)
     */
    private String extractSymbol(String trCd, JsonNode body) {
        return switch (trCd) {
            case "UH1", "US3" -> body.has("shcode") ? body.get("shcode").asText() : null;
            case "CUR" -> body.has("base_id") ? body.get("base_id").asText() : null;
            case "GSH", "GSC" -> body.has("symbol") ? normalizeForeignSymbol(body.get("symbol").asText()) : null;
            case "IJ_" -> body.has("upcode") ? body.get("upcode").asText() : null;
            case "MK2" -> body.has("xsymbol") ? body.get("xsymbol").asText().trim() : null;
            default -> null;
        };
    }

    /**
     * TR key 포맷 (해외주식은 18자리 패딩)
     */
    private String formatTrKey(String trCd, String key) {
        if ("GSH".equals(trCd) || "GSC".equals(trCd)) {
            return String.format("%-18s", resolveForeignRealtimeTrKey(key)).substring(0, 18);
        }
        if ("UH1".equals(trCd) || "US3".equals(trCd)) {
            return String.format("%-10s", "U" + key).substring(0, 10);
        }
        if ("CUR".equals(trCd)) {
            return String.format("%-6s", key).substring(0, 6);
        }
        if ("IJ_".equals(trCd)) {
            return String.format("%-8s", key).substring(0, 8);
        }
        if ("MK2".equals(trCd)) {
            return String.format("%-16s", key).substring(0, 16);
        }
        return key;
    }

    private String resolveForeignRealtimeTrKey(String key) {
        String normalizedKey = normalizeForeignSymbol(key);
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return "";
        }

        if (isResolvedForeignTrKey(normalizedKey)) {
            return normalizedKey;
        }

        var rawExchangeCodes = instrumentRepository.findExchangeCodesByStockCode(normalizedKey);
        log.info("[TR-KEY] DB 거래소코드 조회: symbol={}, rawExchangeCodes={}", normalizedKey, rawExchangeCodes);

        String exchangeCode = rawExchangeCodes.stream()
                .findFirst()
                .map(this::normalizeForeignExchangeCode)
                .orElse("");

        if (exchangeCode.isBlank()) {
            log.warn("[TR-KEY] 거래소코드 조회 실패 → symbol만으로 전송 (LS 인식 불가 가능성): symbol={}", normalizedKey);
            return normalizedKey;
        }

        String trKey = exchangeCode + normalizedKey;
        log.info("[TR-KEY] 최종 tr_key: symbol={}, exchangeCode={}, trKey='{}'", normalizedKey, exchangeCode, trKey);
        return trKey;
    }

    private boolean isResolvedForeignTrKey(String value) {
        return value.length() > 2 && isKnownForeignExchangeCode(value.substring(0, 2));
    }

    private String normalizeForeignExchangeCode(String exchangeCode) {
        if (exchangeCode == null) {
            return "";
        }

        return switch (exchangeCode.trim().toUpperCase(Locale.ROOT)) {
            case "NAS", "NASDAQ", "82" -> "82";
            case "NYS", "NYSE", "AMS", "AMEX", "81" -> "81";
            default -> exchangeCode.trim();
        };
    }

    private boolean isKnownForeignExchangeCode(String exchangeCode) {
        return "81".equals(exchangeCode) || "82".equals(exchangeCode);
    }

    private String normalizeForeignSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
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
     * 마지막 수신 값 조회 (IndexService 등에서 초기값으로 사용)
     * 인메모리 미스 시 Redis 스냅샷 폴백 (서버 재시작 / 장 마감 후 종가 제공)
     */
    public Map<String, String> getAllLastValues() {
        return Map.copyOf(lastValues);
    }

    public String getLastValue(String topic) {
        String value = lastValues.get(topic);
        if (value == null && topic.startsWith("/topic/index/")) {
            value = redisTemplate.opsForValue().get(INDEX_SNAPSHOT_PREFIX + topic);
        }
        if (value == null && topic.startsWith("/topic/currency/")) {
            value = redisTemplate.opsForValue().get(CURRENCY_SNAPSHOT_PREFIX + topic);
        }
        if (value != null) {
            lastValues.put(topic, value); // 인메모리 복구
        }
        return value;
    }

    /**
     * 재연결 후 기존 구독 복원
     */
    private void restoreSubscriptions(String token) {
        activeSubscriptions.clear();
        int restored = 0;
        for (String subscriptionKey : subscriberCount.keySet()) {
            AtomicInteger count = subscriberCount.get(subscriptionKey);
            if (count != null && count.get() > 0) {
                String[] parts = subscriptionKey.split(":", 2);
                registerToLs(parts[0], parts[1], token);
                restored++;
            }
        }
        log.info("[RESTORE] 기존 구독 복원: {}개", restored);

        // 지수 영구 구독 (클라이언트 구독 여부와 무관하게 항상 유지)
        for (String perm : PERMANENT_SUBSCRIPTIONS) {
            String[] parts = perm.split(":", 2);
            registerToLs(parts[0], parts[1], token);
            log.info("[RESTORE] 영구 구독 등록: {}", perm);
        }
    }

    private void registerToLs(String trCd, String key, String token) {
        String subscriptionKey = trCd + ":" + key;
        if (activeSubscriptions.contains(subscriptionKey)) return;

        String trKey = formatTrKey(trCd, key);
        String message = buildLsMessage(token, "3", trCd, trKey);

        log.info("LS 종목 등록 메시지: {}", message);
        outboundSink.tryEmitNext(message);
        activeSubscriptions.add(subscriptionKey);
        log.info("LS 종목 등록: trCd={}, key={}, trKey={}", trCd, key, trKey);
    }
}
