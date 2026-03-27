package com.sollite.order.service;

import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.enums.OrderChannel;
import com.sollite.order.domain.enums.OrderKind;
import com.sollite.order.domain.enums.OrderSide;
import com.sollite.order.domain.repository.OrderRepository;
import com.sollite.order.event.MarketTickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderMatchingService 단위 테스트")
class OrderMatchingServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderExecutionService orderExecutionService;

    @Mock
    private OrderWaitingSubscriptionManager subscriptionManager;

    @Test
    @DisplayName("같은 종목 틱이 연속으로 들어오면 최신 틱 하나만 처리한다")
    void coalescesLatestTickPerSymbol() {
        ControlledExecutor executor = new ControlledExecutor();
        OrderMatchingService service = new OrderMatchingService(
                instrumentRepository,
                orderRepository,
                orderExecutionService,
                subscriptionManager,
                executor
        );

        Instrument instrument = Instrument.builder()
                .marketType("KOSPI")
                .instrumentType("STOCK")
                .stockCode("005930")
                .stockName("삼성전자")
                .currencyCode("KRW")
                .etfYn("N")
                .nxtYn("N")
                .build();
        setField(instrument, "instrumentId", 1L);

        given(instrumentRepository.findByStockCodeAndMarketTypeIn(eq("005930"), eq(List.of("KOSPI", "KOSDAQ"))))
                .willReturn(Optional.of(instrument));
        given(orderRepository.findMatchablePendingBuyLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("101"))))
                .willReturn(List.of());
        given(orderRepository.findMatchablePendingSellLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("101"))))
                .willReturn(List.of());

        service.onMarketTick(new MarketTickEvent("005930", new BigDecimal("100"), "US3", Instant.now()));
        service.onMarketTick(new MarketTickEvent("005930", new BigDecimal("101"), "US3", Instant.now()));

        assertThat(executor.submittedCount()).isEqualTo(1);

        executor.runNext();

        verify(orderRepository, never()).findMatchablePendingBuyLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("100")));
        verify(orderRepository).findMatchablePendingBuyLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("101")));
        verify(orderRepository).findMatchablePendingSellLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("101")));
    }

    @Test
    @DisplayName("매칭 가능한 BUY와 SELL 주문만 각각 조회해 체결한다")
    void processesBuyAndSellCandidatesSeparately() {
        OrderMatchingService service = new OrderMatchingService(
                instrumentRepository,
                orderRepository,
                orderExecutionService,
                subscriptionManager,
                Runnable::run
        );

        Instrument instrument = Instrument.builder()
                .marketType("KOSPI")
                .instrumentType("STOCK")
                .stockCode("005930")
                .stockName("삼성전자")
                .currencyCode("KRW")
                .etfYn("N")
                .nxtYn("N")
                .build();
        setField(instrument, "instrumentId", 1L);

        Order buyOrder = Order.builder()
                .orderNo("BUY-1")
                .orderChannel(OrderChannel.ORDER_FORM)
                .orderSide(OrderSide.BUY)
                .orderKind(OrderKind.LIMIT)
                .orderPrice(new BigDecimal("101"))
                .orderQuantity(1L)
                .reservedAmount(new BigDecimal("101"))
                .build();
        setField(buyOrder, "orderId", 11L);
        Order sellOrder = Order.builder()
                .orderNo("SELL-1")
                .orderChannel(OrderChannel.ORDER_FORM)
                .orderSide(OrderSide.SELL)
                .orderKind(OrderKind.LIMIT)
                .orderPrice(new BigDecimal("99"))
                .orderQuantity(1L)
                .build();
        setField(sellOrder, "orderId", 22L);

        given(instrumentRepository.findByStockCodeAndMarketTypeIn(eq("005930"), eq(List.of("KOSPI", "KOSDAQ"))))
                .willReturn(Optional.of(instrument));
        given(orderRepository.findMatchablePendingBuyLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("100"))))
                .willReturn(List.of(buyOrder));
        given(orderRepository.findMatchablePendingSellLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("100"))))
                .willReturn(List.of(sellOrder));
        given(orderExecutionService.execute(any(), eq(new BigDecimal("100"))))
                .willReturn(true);

        service.onMarketTick(new MarketTickEvent("005930", new BigDecimal("100"), "US3", Instant.now()));

        verify(orderRepository).findMatchablePendingBuyLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("100")));
        verify(orderRepository).findMatchablePendingSellLimitOrders(eq(instrument.getInstrumentId()), eq(new BigDecimal("100")));
        verify(orderExecutionService).execute(eq(buyOrder.getOrderId()), eq(new BigDecimal("100")));
        verify(orderExecutionService).execute(eq(sellOrder.getOrderId()), eq(new BigDecimal("100")));
        verify(subscriptionManager).release(eq(buyOrder.getOrderId()));
        verify(subscriptionManager).release(eq(sellOrder.getOrderId()));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class ControlledExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private int submittedCount;

        @Override
        public void execute(Runnable command) {
            submittedCount++;
            tasks.add(command);
        }

        int submittedCount() {
            return submittedCount;
        }

        void runNext() {
            Runnable task = tasks.poll();
            if (task != null) {
                task.run();
            }
        }
    }
}
