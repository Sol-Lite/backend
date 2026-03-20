package com.sollite.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("LsBrokerService 단위 테스트")
class LsBrokerServiceTest {

    @InjectMocks
    private LsBrokerService lsBrokerService;

    @Mock
    private LsTokenService lsTokenService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private Map<String, AtomicInteger> subscriberCount;
    private Set<String> activeSubscriptions;
    private AtomicBoolean connected;

    @BeforeEach
    void setUp() throws Exception {
        subscriberCount = getField("subscriberCount");
        activeSubscriptions = getField("activeSubscriptions");
        connected = getField("connected");
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field field = LsBrokerService.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(lsBrokerService);
    }

    @Nested
    @DisplayName("subscribe()")
    class Subscribe {

        @Test
        @DisplayName("첫 구독자 등록 시 구독자 수 1")
        void firstSubscriber_countIsOne() {
            lsBrokerService.subscribe("UH1", "005930");

            AtomicInteger count = subscriberCount.get("UH1:005930");
            assertThat(count).isNotNull();
            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("동일 종목 다중 구독 시 카운트 증가")
        void multipleSubscribers_countIncreases() {
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.subscribe("UH1", "005930");

            AtomicInteger count = subscriberCount.get("UH1:005930");
            assertThat(count.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("다른 종목은 별도로 카운트")
        void differentStocks_separateCounts() {
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.subscribe("GSH", "TSLA");
            lsBrokerService.subscribe("GSC", "AAPL");

            assertThat(subscriberCount.get("UH1:005930").get()).isEqualTo(1);
            assertThat(subscriberCount.get("GSH:TSLA").get()).isEqualTo(1);
            assertThat(subscriberCount.get("GSC:AAPL").get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("unsubscribe()")
    class Unsubscribe {

        @Test
        @DisplayName("구독 해제 시 카운트 감소")
        void unsubscribe_decreasesCount() {
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.unsubscribe("UH1", "005930");

            assertThat(subscriberCount.get("UH1:005930").get()).isEqualTo(1);
        }

        @Test
        @DisplayName("마지막 구독자 해제 시 subscriberCount에서 제거")
        void lastUnsubscribe_removesFromMap() {
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.unsubscribe("UH1", "005930");

            assertThat(subscriberCount).doesNotContainKey("UH1:005930");
        }

        @Test
        @DisplayName("마지막 구독자 해제 시 activeSubscriptions에서 제거")
        void lastUnsubscribe_removesFromActive() {
            activeSubscriptions.add("UH1:005930");

            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.unsubscribe("UH1", "005930");

            assertThat(activeSubscriptions).doesNotContain("UH1:005930");
        }

        @Test
        @DisplayName("구독하지 않은 종목 해제 시 무시")
        void unsubscribeNonExistent_ignored() {
            lsBrokerService.unsubscribe("UH1", "999999");

            assertThat(subscriberCount).isEmpty();
        }
    }

    @Nested
    @DisplayName("구독/해제 통합 시나리오")
    class SubscribeUnsubscribeScenario {

        @Test
        @DisplayName("여러 종목 구독 후 일부 해제")
        void mixedSubscribeUnsubscribe() {
            // 3명이 삼성전자 구독
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.subscribe("UH1", "005930");
            lsBrokerService.subscribe("UH1", "005930");

            // 2명이 TSLA 구독
            lsBrokerService.subscribe("GSH", "TSLA");
            lsBrokerService.subscribe("GSH", "TSLA");

            // 삼성전자 1명 해제
            lsBrokerService.unsubscribe("UH1", "005930");

            assertThat(subscriberCount.get("UH1:005930").get()).isEqualTo(2);
            assertThat(subscriberCount.get("GSH:TSLA").get()).isEqualTo(2);
        }

        @Test
        @DisplayName("모든 구독자 해제 후 재구독")
        void resubscribeAfterAllUnsubscribed() {
            lsBrokerService.subscribe("GSH", "TSLA");
            lsBrokerService.unsubscribe("GSH", "TSLA");

            // 완전히 해제됨
            assertThat(subscriberCount).doesNotContainKey("GSH:TSLA");

            // 재구독
            lsBrokerService.subscribe("GSH", "TSLA");
            assertThat(subscriberCount.get("GSH:TSLA").get()).isEqualTo(1);
        }
    }
}
