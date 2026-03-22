package com.sollite.websocket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StompSubscriptionListener 단위 테스트")
class StompSubscriptionListenerTest {

    @InjectMocks
    private StompSubscriptionListener listener;

    @Mock
    private LsBrokerService lsBrokerService;

    private SessionSubscribeEvent createSubscribeEvent(String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(
                org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionSubscribeEvent(this, message);
    }

    private SessionDisconnectEvent createDisconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(
                org.springframework.messaging.simp.stomp.StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }

    @Nested
    @DisplayName("handleSubscribe()")
    class HandleSubscribe {

        @Test
        @DisplayName("국내 호가 구독 시 UH1 TR코드로 LsBrokerService 호출")
        void domesticAsking_subscribesWithH1() {
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/asking/005930"));

            verify(lsBrokerService).subscribe("UH1", "005930");
        }

        @Test
        @DisplayName("해외 호가 구독 시 GSH TR코드로 LsBrokerService 호출")
        void foreignQuote_subscribesWithGSH() {
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/foreign/quote/TSLA"));

            verify(lsBrokerService).subscribe("GSH", "TSLA");
        }

        @Test
        @DisplayName("해외 체결 구독 시 GSC TR코드로 LsBrokerService 호출")
        void foreignTransaction_subscribesWithGSC() {
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/foreign/transaction/AAPL"));

            verify(lsBrokerService).subscribe("GSC", "AAPL");
        }

        @Test
        @DisplayName("알 수 없는 destination은 무시")
        void unknownDestination_ignored() {
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/unknown/something"));

            verifyNoInteractions(lsBrokerService);
        }
    }

    @Nested
    @DisplayName("handleDisconnect()")
    class HandleDisconnect {

        @Test
        @DisplayName("세션 종료 시 해당 세션의 모든 구독 해제")
        void disconnect_unsubscribesAll() {
            // 여러 종목 구독
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/asking/005930"));
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/foreign/quote/TSLA"));
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/foreign/transaction/AAPL"));

            // 세션 종료
            listener.handleDisconnect(createDisconnectEvent("session1"));

            verify(lsBrokerService).unsubscribe("UH1", "005930");
            verify(lsBrokerService).unsubscribe("GSH", "TSLA");
            verify(lsBrokerService).unsubscribe("GSC", "AAPL");
        }

        @Test
        @DisplayName("다른 세션의 구독은 영향 없음")
        void disconnect_doesNotAffectOtherSessions() {
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/asking/005930"));
            listener.handleSubscribe(createSubscribeEvent("session2", "/topic/foreign/quote/TSLA"));

            // session1만 종료
            listener.handleDisconnect(createDisconnectEvent("session1"));

            verify(lsBrokerService).unsubscribe("UH1", "005930");
            verify(lsBrokerService, never()).unsubscribe("GSH", "TSLA");
        }

        @Test
        @DisplayName("구독 없는 세션 종료 시 에러 없음")
        void disconnectWithoutSubscriptions_noError() {
            listener.handleDisconnect(createDisconnectEvent("unknown-session"));

            verifyNoInteractions(lsBrokerService);
        }
    }

    @Nested
    @DisplayName("다중 세션 시나리오")
    class MultiSessionScenario {

        @Test
        @DisplayName("같은 종목을 다른 세션에서 구독 후 한 세션만 종료")
        void sameStockDifferentSessions_oneDisconnect() {
            listener.handleSubscribe(createSubscribeEvent("session1", "/topic/asking/005930"));
            listener.handleSubscribe(createSubscribeEvent("session2", "/topic/asking/005930"));

            listener.handleDisconnect(createDisconnectEvent("session1"));

            // session1의 구독만 해제
            verify(lsBrokerService, times(1)).unsubscribe("UH1", "005930");
            // session2의 구독은 여전히 유효 (subscribe 2번 호출됨)
            verify(lsBrokerService, times(2)).subscribe("UH1", "005930");
        }
    }
}
