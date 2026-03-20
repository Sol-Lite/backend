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

        if (destination == null || sessionId == null) return;

        SubscriptionInfo info = parseDestination(destination);
        if (info == null) return;

        // 세션별 구독 목록에 추가
        sessionSubscriptions
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(info);

        lsBrokerService.subscribe(info.trCd(), info.key());
        log.info("STOMP 구독: sessionId={}, destination={}", sessionId, destination);
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        // STOMP unsubscribe는 destination 정보가 없어 subscriptionId로 관리해야 하지만
        // disconnect에서 일괄 정리하므로 여기서는 로그만
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("STOMP 구독 해제: sessionId={}", accessor.getSessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        Set<SubscriptionInfo> subscriptions = sessionSubscriptions.remove(sessionId);
        if (subscriptions == null) return;

        for (SubscriptionInfo info : subscriptions) {
            lsBrokerService.unsubscribe(info.trCd(), info.key());
        }

        log.info("STOMP 연결 종료: sessionId={}, 구독 {}개 해제", sessionId, subscriptions.size());
    }

    /**
     * STOMP destination → (trCd, key) 파싱
     *
     * /topic/asking/005930           → ("UH1", "005930")
     * /topic/foreign/quote/TSLA      → ("GSH", "TSLA")
     * /topic/foreign/transaction/TSLA → ("GSC", "TSLA")
     */
    private SubscriptionInfo parseDestination(String destination) {
        if (destination.startsWith("/topic/asking/")) {
            String stockCode = destination.substring("/topic/asking/".length());
            return new SubscriptionInfo("UH1", stockCode);
        }
        if (destination.startsWith("/topic/foreign/quote/")) {
            String symbol = destination.substring("/topic/foreign/quote/".length());
            return new SubscriptionInfo("GSH", symbol);
        }
        if (destination.startsWith("/topic/foreign/transaction/")) {
            String symbol = destination.substring("/topic/foreign/transaction/".length());
            return new SubscriptionInfo("GSC", symbol);
        }
        return null;
    }

    private record SubscriptionInfo(String trCd, String key) {}
}
