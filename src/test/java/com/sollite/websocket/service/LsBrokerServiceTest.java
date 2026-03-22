package com.sollite.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.market.domain.repository.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private InstrumentRepository instrumentRepository;

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

    @Nested
    @DisplayName("실시간 메시지 처리")
    class HandleLsMessage {

        @Test
        @DisplayName("해외 실시간 symbol 공백 패딩은 제거하고 토픽으로 발행한다")
        void foreignRealtimeSymbol_isTrimmedBeforePublish() throws Exception {
            String payload = """
                    {
                      "header": { "tr_cd": "GSH" },
                      "body": {
                        "symbol": "TSLA              ",
                        "price": "250.75"
                      }
                    }
                    """;
            JsonNode root = new ObjectMapper().readTree(payload);
            given(objectMapper.readTree(payload)).willReturn(root);

            Method handleLsMessage = LsBrokerService.class.getDeclaredMethod("handleLsMessage", String.class);
            handleLsMessage.setAccessible(true);
            handleLsMessage.invoke(lsBrokerService, payload);

            verify(messagingTemplate).convertAndSend("/topic/foreign/quote/TSLA", root.get("body").toString());
            assertThat(lsBrokerService.getLastValue("/topic/foreign/quote/TSLA")).isEqualTo(root.get("body").toString());
        }
    }

    @Nested
    @DisplayName("해외 실시간 tr_key 생성")
    class ForeignRealtimeTrKey {

        @Test
        @DisplayName("심볼만 들어오면 종목 테이블의 거래소코드를 붙여 18자리 tr_key를 만든다")
        void symbolOnlyKey_resolvesExchangeCodeFromInstrument() throws Exception {
            given(instrumentRepository.findFirstExchangeCodeByStockCode("TSLA")).willReturn(Optional.of("82"));

            Method formatTrKey = LsBrokerService.class.getDeclaredMethod("formatTrKey", String.class, String.class);
            formatTrKey.setAccessible(true);

            String trKey = (String) formatTrKey.invoke(lsBrokerService, "GSH", "TSLA");

            assertThat(trKey).isEqualTo("82TSLA            ");
        }

        @Test
        @DisplayName("이미 거래소코드가 붙은 키는 그대로 18자리 패딩한다")
        void resolvedKey_keepsExchangeCodePrefix() throws Exception {
            Method formatTrKey = LsBrokerService.class.getDeclaredMethod("formatTrKey", String.class, String.class);
            formatTrKey.setAccessible(true);

            String trKey = (String) formatTrKey.invoke(lsBrokerService, "GSH", "81SOXL");

            assertThat(trKey).isEqualTo("81SOXL            ");
        }
    }
}
