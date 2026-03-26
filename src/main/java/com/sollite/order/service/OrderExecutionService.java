package com.sollite.order.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.balance.domain.entity.CashBalance;
import com.sollite.balance.domain.entity.CashLedger;
import com.sollite.balance.domain.entity.Holding;
import com.sollite.balance.domain.entity.PositionLedger;
import com.sollite.balance.domain.enums.CashEntryType;
import com.sollite.balance.domain.enums.PositionEntryType;
import com.sollite.balance.domain.repository.CashBalanceRepository;
import com.sollite.balance.domain.repository.CashLedgerRepository;
import com.sollite.balance.domain.repository.HoldingRepository;
import com.sollite.balance.domain.repository.PositionLedgerRepository;
import com.sollite.balance.service.PriceLookupService;
import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.order.domain.entity.Execution;
import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.entity.OrderEvent;
import com.sollite.order.domain.enums.OrderEventType;
import com.sollite.order.domain.enums.OrderKind;
import com.sollite.order.domain.enums.OrderSide;
import com.sollite.order.domain.enums.OrderStatus;
import com.sollite.order.domain.repository.ExecutionRepository;
import com.sollite.order.domain.repository.OrderEventRepository;
import com.sollite.order.domain.repository.OrderRepository;
import com.sollite.notifications.event.ExecutionNotificationEvent;
import com.sollite.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 주문 체결 전용 서비스.
 * 체결(Execution), 원장(CashLedger/PositionLedger), 잔고(CashBalance/Holding) 반영을 여기서만 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExecutionService {

    private static final DateTimeFormatter EXEC_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderRepository orderRepository;
    private final ExecutionRepository executionRepository;
    private final OrderEventRepository orderEventRepository;
    private final CashBalanceRepository cashBalanceRepository;
    private final CashLedgerRepository cashLedgerRepository;
    private final HoldingRepository holdingRepository;
    private final PositionLedgerRepository positionLedgerRepository;
    private final PriceLookupService priceLookupService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 주문 체결 (매수/매도 공통 진입점).
     * <p>
     * 1. SELECT FOR UPDATE 으로 Order 락 + PENDING 재검증 (중복 체결 방어)
     * 2. 매수/매도 분기 후 원장/잔고 반영
     *
     * @param orderId   체결 대상 주문 ID
     * @param fillPrice 체결 가격 (틱 가격 또는 현재가)
     * @return true 체결 성공, false 이미 체결됨/취소됨 (중복 방어)
     */
    @Transactional
    public boolean execute(Long orderId, BigDecimal fillPrice) {
        // 1. FOR UPDATE 락 + 상태 재검증
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.isPending()) {
            log.info("[EXEC] 이미 처리된 주문 스킵 — orderId={}, status={}", orderId, order.getOrderStatus());
            return false;
        }

        // 2. 매수/매도 분기
        if (order.getOrderSide() == OrderSide.BUY) {
            executeBuy(order, fillPrice);
        } else {
            executeSell(order, fillPrice);
        }

        log.info("[EXEC] 체결 완료 — orderId={}, side={}, instrument={}, qty={}, price={}",
                orderId, order.getOrderSide(), order.getInstrument().getStockCode(),
                order.getOrderQuantity(), fillPrice);

        return true;
    }

    // -----------------------------------------------------------------------
    // 매수 체결
    // -----------------------------------------------------------------------

    private void executeBuy(Order order, BigDecimal fillPrice) {
        Account account = order.getAccount();
        SimulationRound round = order.getSimulationRound();
        Instrument instrument = order.getInstrument();
        String currencyCode = instrument.getCurrencyCode();

        BigDecimal cost = fillPrice.multiply(BigDecimal.valueOf(order.getOrderQuantity()));

        // 1. Order → FILLED
        order.fill();

        // 2. Execution INSERT
        Execution execution = Execution.builder()
                .order(order)
                .account(account)
                .simulationRound(round)
                .instrument(instrument)
                .executionNo(generateExecutionNo())
                .orderSide(OrderSide.BUY)
                .executionPrice(fillPrice)
                .executionQuantity(order.getOrderQuantity())
                .grossAmount(cost)
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .netAmount(cost)
                .build();
        executionRepository.save(execution);

        // 3. CashBalance: 정산
        CashBalance cashBalance = cashBalanceRepository.findForUpdate(
                        account.getAccountId(), round.getSimulationRoundId(), currencyCode)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INSUFFICIENT_CASH));

        // LIMIT 주문: 예약금과 실제 체결금의 차이 조정
        // 예약금(reservedAmount)은 주문가 기준, 체결금(cost)은 틱가 기준
        // 예약금 해제 후 실제 체결금으로 정산
        BigDecimal reservedAmount = order.getReservedAmount();
        if (reservedAmount != null && reservedAmount.compareTo(BigDecimal.ZERO) > 0) {
            // available: 예약금 해제 + 실제 체결금 차감
            // 즉, available += (reservedAmount - cost)
            BigDecimal refundDiff = reservedAmount.subtract(cost);
            if (refundDiff.compareTo(BigDecimal.ZERO) != 0) {
                cashBalance.cancelBuyReserve(refundDiff);
            }
        }
        cashBalance.settleForBuy(cost);

        // 4. CashLedger INSERT (불변 이력)
        cashLedgerRepository.save(CashLedger.builder()
                .account(account)
                .simulationRound(round)
                .currencyCode(currencyCode)
                .entryType(CashEntryType.BUY_SETTLE)
                .amountDelta(cost.negate())
                .balanceAfter(cashBalance.getTotalAmount())
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());

        // 5. Holdings UPSERT (SELECT FOR UPDATE)
        Holding holding = holdingRepository
                .findForUpdate(account.getAccountId(), round.getSimulationRoundId(), instrument.getInstrumentId())
                .orElseGet(() -> Holding.builder()
                        .account(account)
                        .simulationRound(round)
                        .instrument(instrument)
                        .holdingQuantity(0L)
                        .availableQuantity(0L)
                        .avgBuyPrice(BigDecimal.ZERO)
                        .avgBuyExchangeRate(BigDecimal.ONE)
                        .build());

        BigDecimal exchangeRate = "USD".equals(currencyCode)
                ? resolveExchangeRate()
                : BigDecimal.ONE;
        holding.addBuyFill(order.getOrderQuantity(), fillPrice, exchangeRate);
        holdingRepository.save(holding);

        // 6. PositionLedger INSERT (불변 이력)
        positionLedgerRepository.save(PositionLedger.builder()
                .account(account)
                .simulationRound(round)
                .instrument(instrument)
                .entryType(PositionEntryType.BUY_FILL)
                .quantityDelta(order.getOrderQuantity())
                .holdingQuantityAfter(holding.getHoldingQuantity())
                .avgBuyPriceAfter(holding.getAvgBuyPrice())
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());

        // 7. OrderEvent INSERT
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.FILLED)
                .beforeStatus(OrderStatus.PENDING)
                .afterStatus(OrderStatus.FILLED)
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());

        // 8. 체결 알림 이벤트 발행 (AFTER_COMMIT 리스너가 트랜잭션 커밋 후 처리)
        eventPublisher.publishEvent(new ExecutionNotificationEvent(
                account.getUser().getUserId(),
                instrument.getStockCode(),
                instrument.getStockName(),
                OrderSide.BUY.name(),
                order.getOrderQuantity(),
                fillPrice,
                currencyCode
        ));
    }

    // -----------------------------------------------------------------------
    // 매도 체결
    // -----------------------------------------------------------------------

    private void executeSell(Order order, BigDecimal fillPrice) {
        Account account = order.getAccount();
        SimulationRound round = order.getSimulationRound();
        Instrument instrument = order.getInstrument();
        String currencyCode = instrument.getCurrencyCode();

        BigDecimal proceeds = fillPrice.multiply(BigDecimal.valueOf(order.getOrderQuantity()));

        // 1. Order → FILLED
        order.fill();

        // 2. Execution INSERT
        Execution execution = Execution.builder()
                .order(order)
                .account(account)
                .simulationRound(round)
                .instrument(instrument)
                .executionNo(generateExecutionNo())
                .orderSide(OrderSide.SELL)
                .executionPrice(fillPrice)
                .executionQuantity(order.getOrderQuantity())
                .grossAmount(proceeds)
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .netAmount(proceeds)
                .build();
        executionRepository.save(execution);

        // 3. CashBalance UPDATE (available + total 모두 증가)
        CashBalance cashBalance = cashBalanceRepository.findForUpdate(
                        account.getAccountId(), round.getSimulationRoundId(), currencyCode)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));
        cashBalance.settleForSell(proceeds);

        // 4. CashLedger INSERT (불변 이력)
        cashLedgerRepository.save(CashLedger.builder()
                .account(account)
                .simulationRound(round)
                .currencyCode(currencyCode)
                .entryType(CashEntryType.SELL_SETTLE)
                .amountDelta(proceeds)
                .balanceAfter(cashBalance.getTotalAmount())
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());

        // 5. Holdings UPDATE: holding_quantity 차감 (available은 접수 시 이미 차감됨)
        Holding holding = holdingRepository
                .findForUpdate(account.getAccountId(), round.getSimulationRoundId(), instrument.getInstrumentId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.HOLDING_NOT_FOUND));
        holding.subtractSellFill(order.getOrderQuantity());

        // 6. PositionLedger INSERT (불변 이력)
        positionLedgerRepository.save(PositionLedger.builder()
                .account(account)
                .simulationRound(round)
                .instrument(instrument)
                .entryType(PositionEntryType.SELL_FILL)
                .quantityDelta(-order.getOrderQuantity())
                .holdingQuantityAfter(holding.getHoldingQuantity())
                .avgBuyPriceAfter(holding.getAvgBuyPrice())
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());

        // 7. OrderEvent INSERT
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.FILLED)
                .beforeStatus(OrderStatus.PENDING)
                .afterStatus(OrderStatus.FILLED)
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());

        // 8. 체결 알림 이벤트 발행 (AFTER_COMMIT 리스너가 트랜잭션 커밋 후 처리)
        eventPublisher.publishEvent(new ExecutionNotificationEvent(
                account.getUser().getUserId(),
                instrument.getStockCode(),
                instrument.getStockName(),
                OrderSide.SELL.name(),
                order.getOrderQuantity(),
                fillPrice,
                currencyCode
        ));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String generateExecutionNo() {
        String timestamp = LocalDateTime.now().format(EXEC_NO_FMT);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "EXE" + timestamp + random;
    }

    private BigDecimal resolveExchangeRate() {
        BigDecimal rate = priceLookupService.resolveUsdKrwRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(OrderErrorCode.EXCHANGE_RATE_UNAVAILABLE);
        }
        return rate;
    }
}
