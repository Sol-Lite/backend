package com.sollite.websocket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompSubscriptionListener {

    private final LsBrokerService lsBrokerService;

    // sessionId → Set<"trCd:key"> (세션별 구독 목록, disconnect 시 정리용)
    private final Map<String, Set<SubscriptionInfo>> sessionSubscriptions = new ConcurrentHashMap<>();

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();

        log.info("[STOMP] SUBSCRIBE: sessionId={}, destination={}", sessionId, destination);

        if (destination == null || sessionId == null) {
            log.warn("[STOMP] SUBSCRIBE 무시: destination 또는 sessionId 없음");
            return;
        }

        SubscriptionInfo info = parseDestination(destination);
        if (info == null) {
            log.warn("[STOMP] SUBSCRIBE 무시: 매핑 없는 destination={}", destination);
            return;
        }

        log.info("[STOMP] SUBSCRIBE 파싱 성공: trCd={}, key={}", info.trCd(), info.key());

        sessionSubscriptions
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(info);

        lsBrokerService.subscribe(info.trCd(), info.key());
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("[STOMP] UNSUBSCRIBE: sessionId={}", accessor.getSessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        Set<SubscriptionInfo> subscriptions = sessionSubscriptions.remove(sessionId);
        if (subscriptions == null) {
            log.info("[STOMP] DISCONNECT: sessionId={}, 구독 없음", sessionId);
            return;
        }

        log.info("[STOMP] DISCONNECT: sessionId={}, 구독 {}개 해제 시작", sessionId, subscriptions.size());
        for (SubscriptionInfo info : subscriptions) {
            log.info("[STOMP] DISCONNECT 구독 해제: trCd={}, key={}", info.trCd(), info.key());
            lsBrokerService.unsubscribe(info.trCd(), info.key());
        }
    }

    /**
     * STOMP destination → (trCd, key) 파싱
     *
     * /topic/asking/005930            → ("UH1", "005930")
     * /topic/foreign/quote/TSLA       → ("GSH", "TSLA")
     * /topic/foreign/transaction/TSLA → ("GSC", "TSLA")
     * /topic/index/domestic/001       → ("IJ_", "001")
     * /topic/index/foreign/SPI@SPX    → ("MK2", "SPI@SPX")
     */
    private SubscriptionInfo parseDestination(String destination) {
        if (destination.startsWith("/topic/asking/")) {
            return new SubscriptionInfo("UH1", destination.substring("/topic/asking/".length()));
        }
        if (destination.startsWith("/topic/stock/trade/")) {
            return new SubscriptionInfo("US3", destination.substring("/topic/stock/trade/".length()));
        }
        if (destination.startsWith("/topic/currency/")) {
            return new SubscriptionInfo("CUR", destination.substring("/topic/currency/".length()));
        }
        if (destination.startsWith("/topic/foreign/quote/")) {
            return new SubscriptionInfo("GSH", destination.substring("/topic/foreign/quote/".length()));
        }
        if (destination.startsWith("/topic/foreign/transaction/")) {
            return new SubscriptionInfo("GSC", destination.substring("/topic/foreign/transaction/".length()));
        }
        if (destination.startsWith("/topic/index/domestic/")) {
            return new SubscriptionInfo("IJ_", destination.substring("/topic/index/domestic/".length()));
        }
        if (destination.startsWith("/topic/index/foreign/")) {
            return new SubscriptionInfo("MK2", destination.substring("/topic/index/foreign/".length()));
        }
        return null;
    }

    private record SubscriptionInfo(String trCd, String key) {}
}
