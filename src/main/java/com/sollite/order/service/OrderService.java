package com.sollite.order.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.RoundStatus;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
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
import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.order.domain.entity.Execution;
import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.entity.OrderEvent;
import com.sollite.order.domain.enums.OrderEventType;
import com.sollite.order.domain.enums.OrderSide;
import com.sollite.order.domain.enums.OrderStatus;
import com.sollite.order.domain.repository.ExecutionRepository;
import com.sollite.order.domain.repository.OrderEventRepository;
import com.sollite.order.domain.repository.OrderRepository;
import com.sollite.order.dto.AmendOrderRequest;
import com.sollite.order.dto.ExecutionResponse;
import com.sollite.order.dto.OrderDetailResponse;
import com.sollite.order.dto.OrderResponse;
import com.sollite.order.dto.PlaceOrderRequest;
import com.sollite.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String CURRENCY_KRW = "KRW";
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AccountRepository accountRepository;
    private final SimulationRoundRepository simulationRoundRepository;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final ExecutionRepository executionRepository;
    private final CashBalanceRepository cashBalanceRepository;
    private final CashLedgerRepository cashLedgerRepository;
    private final HoldingRepository holdingRepository;
    private final PositionLedgerRepository positionLedgerRepository;

    // -----------------------------------------------------------------------
    // 주문 접수 (매수/매도 즉시 체결 시뮬레이션)
    // -----------------------------------------------------------------------

    @Transactional
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest req) {
        // 1. 계좌 & 라운드 조회
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        SimulationRound round = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));

        // 2. 종목 조회
        Instrument instrument = instrumentRepository.findById(req.instrumentId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INSTRUMENT_NOT_FOUND));

        // 3. 수량 검증
        if (req.orderQuantity() < 1) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_QUANTITY);
        }

        // 4. 멱등성 키 중복 체크
        String idempotencyKey = resolveIdempotencyKey(req.idempotencyKey());
        Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("[ORDER] 중복 요청 — idempotencyKey={}", idempotencyKey);
            return OrderResponse.from(existing.get());
        }

        // 5. 주문 가격 결정
        BigDecimal fillPrice = resolveFillPrice(req, instrument);

        // 6. 매수/매도 분기 처리
        if (req.orderSide() == OrderSide.BUY) {
            return processBuyOrder(account, round, instrument, req, idempotencyKey, fillPrice);
        } else {
            return processSellOrder(account, round, instrument, req, idempotencyKey, fillPrice);
        }
    }

    // -----------------------------------------------------------------------
    // 매수 처리
    // -----------------------------------------------------------------------

    private OrderResponse processBuyOrder(Account account, SimulationRound round, Instrument instrument,
                                          PlaceOrderRequest req, String idempotencyKey, BigDecimal fillPrice) {
        BigDecimal cost = fillPrice.multiply(BigDecimal.valueOf(req.orderQuantity()));
        String currencyCode = instrument.getCurrencyCode();

        // 1. cash_balances SELECT FOR UPDATE
        CashBalance cashBalance = cashBalanceRepository.findForUpdate(
                        account.getAccountId(), round.getSimulationRoundId(), currencyCode)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INSUFFICIENT_CASH));

        // 2. 가용금액 검증
        if (cashBalance.getAvailableAmount().compareTo(cost) < 0) {
            throw new BusinessException(OrderErrorCode.INSUFFICIENT_CASH);
        }

        // 3. 주문 INSERT (PENDING)
        Order order = Order.builder()
                .account(account)
                .simulationRound(round)
                .instrument(instrument)
                .orderNo(generateOrderNo())
                .orderChannel(req.orderChannel())
                .orderSide(req.orderSide())
                .orderKind(req.orderKind())
                .orderPrice(fillPrice)
                .orderQuantity(req.orderQuantity())
                .reservedAmount(cost)
                .idempotencyKey(idempotencyKey)
                .build();
        orderRepository.save(order);

        // 4. 가용금액 차감 (reserve)
        cashBalance.reserveForBuy(cost);

        // 5. OrderEvent: PLACED
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.PLACED)
                .beforeStatus(null)
                .afterStatus(OrderStatus.PENDING)
                .build());

        // 6. 즉시 체결 처리
        fillBuyOrder(order, account, round, instrument, cashBalance, cost, currencyCode, fillPrice);

        log.info("[ORDER] 매수 체결 완료 — orderId={}, instrument={}, qty={}, price={}",
                order.getOrderId(), instrument.getStockCode(), req.orderQuantity(), fillPrice);

        return OrderResponse.from(order);
    }

    private void fillBuyOrder(Order order, Account account, SimulationRound round,
                               Instrument instrument, CashBalance cashBalance,
                               BigDecimal cost, String currencyCode, BigDecimal fillPrice) {
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

        // 3. CashLedger: BUY_SETTLE (total 차감)
        cashBalance.settleForBuy(cost);
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

        // 4. Holdings UPSERT (SELECT FOR UPDATE)
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

        holding.addBuyFill(order.getOrderQuantity(), fillPrice);
        holdingRepository.save(holding);

        // 5. PositionLedger: BUY_FILL
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

        // 6. OrderEvent: FILLED
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.FILLED)
                .beforeStatus(OrderStatus.PENDING)
                .afterStatus(OrderStatus.FILLED)
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());
    }

    // -----------------------------------------------------------------------
    // 매도 처리
    // -----------------------------------------------------------------------

    private OrderResponse processSellOrder(Account account, SimulationRound round, Instrument instrument,
                                           PlaceOrderRequest req, String idempotencyKey, BigDecimal fillPrice) {
        BigDecimal proceeds = fillPrice.multiply(BigDecimal.valueOf(req.orderQuantity()));
        String currencyCode = instrument.getCurrencyCode();

        // 1. holdings SELECT FOR UPDATE
        Holding holding = holdingRepository
                .findForUpdate(account.getAccountId(), round.getSimulationRoundId(), instrument.getInstrumentId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.HOLDING_NOT_FOUND));

        // 2. 가용수량 검증
        if (holding.getAvailableQuantity() < req.orderQuantity()) {
            throw new BusinessException(OrderErrorCode.INSUFFICIENT_HOLDINGS);
        }

        // 3. 주문 INSERT (PENDING)
        Order order = Order.builder()
                .account(account)
                .simulationRound(round)
                .instrument(instrument)
                .orderNo(generateOrderNo())
                .orderChannel(req.orderChannel())
                .orderSide(req.orderSide())
                .orderKind(req.orderKind())
                .orderPrice(fillPrice)
                .orderQuantity(req.orderQuantity())
                .idempotencyKey(idempotencyKey)
                .build();
        orderRepository.save(order);

        // 4. 가용수량 차감 (reserve)
        holding.reserveForSell(req.orderQuantity());

        // 5. OrderEvent: PLACED
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.PLACED)
                .beforeStatus(null)
                .afterStatus(OrderStatus.PENDING)
                .build());

        // 6. 즉시 체결 처리
        fillSellOrder(order, account, round, instrument, holding, proceeds, currencyCode, fillPrice);

        log.info("[ORDER] 매도 체결 완료 — orderId={}, instrument={}, qty={}, price={}",
                order.getOrderId(), instrument.getStockCode(), req.orderQuantity(), fillPrice);

        return OrderResponse.from(order);
    }

    private void fillSellOrder(Order order, Account account, SimulationRound round,
                                Instrument instrument, Holding holding,
                                BigDecimal proceeds, String currencyCode, BigDecimal fillPrice) {
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

        // 3. cash_balances UPDATE (available + total 모두 증가)
        CashBalance cashBalance = cashBalanceRepository.findForUpdate(
                        account.getAccountId(), round.getSimulationRoundId(), currencyCode)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));
        cashBalance.settleForSell(proceeds);

        // 4. CashLedger: SELL_SETTLE
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
        holding.subtractSellFill(order.getOrderQuantity());

        // 6. PositionLedger: SELL_FILL
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

        // 7. OrderEvent: FILLED
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.FILLED)
                .beforeStatus(OrderStatus.PENDING)
                .afterStatus(OrderStatus.FILLED)
                .referenceType("EXECUTION")
                .referenceId(String.valueOf(execution.getExecutionId()))
                .build());
    }

    // -----------------------------------------------------------------------
    // 주문 조회
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(Long userId, String status) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        SimulationRound round = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));

        List<Order> orders;
        if ("ALL".equalsIgnoreCase(status) || status == null) {
            orders = orderRepository
                    .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdOrderByRequestedAtDesc(
                            account.getAccountId(), round.getSimulationRoundId());
        } else {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            orders = orderRepository
                    .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndOrderStatusOrderByRequestedAtDesc(
                            account.getAccountId(), round.getSimulationRoundId(), orderStatus);
        }

        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        validateOwnership(userId, order);

        ExecutionResponse executionResponse = executionRepository.findByOrder_OrderId(orderId)
                .map(ExecutionResponse::from)
                .orElse(null);

        return new OrderDetailResponse(OrderResponse.from(order), executionResponse);
    }

    // -----------------------------------------------------------------------
    // 주문 취소
    // -----------------------------------------------------------------------

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        validateOwnership(userId, order);

        if (!order.isPending()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }

        Account account = order.getAccount();
        SimulationRound round = order.getSimulationRound();
        Instrument instrument = order.getInstrument();

        if (order.getOrderSide() == OrderSide.BUY) {
            // 매수 취소: 가용금액 복원
            CashBalance cashBalance = cashBalanceRepository.findForUpdate(
                            account.getAccountId(), round.getSimulationRoundId(), instrument.getCurrencyCode())
                    .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));
            cashBalance.cancelBuyReserve(order.getReservedAmount());

            cashLedgerRepository.save(CashLedger.builder()
                    .account(account)
                    .simulationRound(round)
                    .currencyCode(instrument.getCurrencyCode())
                    .entryType(CashEntryType.BUY_RESERVE_CANCEL)
                    .amountDelta(order.getReservedAmount())
                    .balanceAfter(cashBalance.getAvailableAmount())
                    .referenceType("ORDER")
                    .referenceId(String.valueOf(orderId))
                    .build());
        } else {
            // 매도 취소: 가용수량 복원
            holdingRepository.findForUpdate(account.getAccountId(), round.getSimulationRoundId(),
                            instrument.getInstrumentId())
                    .ifPresent(h -> h.cancelSellReserve(order.getOrderQuantity()));
        }

        order.cancel();

        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.CANCELLED)
                .beforeStatus(OrderStatus.PENDING)
                .afterStatus(OrderStatus.CANCELLED)
                .build());

        log.info("[ORDER] 주문 취소 완료 — orderId={}", orderId);
        return OrderResponse.from(order);
    }

    // -----------------------------------------------------------------------
    // 미체결 전체 취소
    // -----------------------------------------------------------------------

    @Transactional
    public int cancelAllPendingOrders(Long userId) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        SimulationRound round = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));

        List<Order> pendingOrders = orderRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndOrderStatusIn(
                        account.getAccountId(), round.getSimulationRoundId(), List.of(OrderStatus.PENDING));

        int count = 0;
        for (Order order : pendingOrders) {
            cancelOrder(userId, order.getOrderId());
            count++;
        }

        log.info("[ORDER] 미체결 전체 취소 완료 — userId={}, count={}", userId, count);
        return count;
    }

    // -----------------------------------------------------------------------
    // 주문 정정 (PENDING 상태의 LIMIT 주문만)
    // -----------------------------------------------------------------------

    @Transactional
    public OrderResponse amendOrder(Long userId, Long orderId, AmendOrderRequest req) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        validateOwnership(userId, order);

        if (!order.isPending()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_AMENDABLE);
        }

        Account account = order.getAccount();
        SimulationRound round = order.getSimulationRound();
        Instrument instrument = order.getInstrument();
        BigDecimal newCost = req.orderPrice().multiply(BigDecimal.valueOf(req.orderQuantity()));

        if (order.getOrderSide() == OrderSide.BUY) {
            CashBalance cashBalance = cashBalanceRepository.findForUpdate(
                            account.getAccountId(), round.getSimulationRoundId(), instrument.getCurrencyCode())
                    .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));

            // 기존 예약금 복원 후 새 금액 검증
            BigDecimal adjustedAvailable = cashBalance.getAvailableAmount().add(order.getReservedAmount());
            if (adjustedAvailable.compareTo(newCost) < 0) {
                throw new BusinessException(OrderErrorCode.INSUFFICIENT_CASH);
            }

            // available 재계산
            cashBalance.cancelBuyReserve(order.getReservedAmount());
            cashBalance.reserveForBuy(newCost);
        } else {
            Holding holding = holdingRepository.findForUpdate(
                            account.getAccountId(), round.getSimulationRoundId(), instrument.getInstrumentId())
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.HOLDING_NOT_FOUND));

            long adjustedAvailable = holding.getAvailableQuantity() + order.getOrderQuantity();
            if (adjustedAvailable < req.orderQuantity()) {
                throw new BusinessException(OrderErrorCode.INSUFFICIENT_HOLDINGS);
            }

            holding.cancelSellReserve(order.getOrderQuantity());
            holding.reserveForSell(req.orderQuantity());
        }

        order.amend(req.orderPrice(), req.orderQuantity(), newCost);

        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.AMENDED)
                .beforeStatus(OrderStatus.PENDING)
                .afterStatus(OrderStatus.PENDING)
                .eventMessage("정정: price=" + req.orderPrice() + ", qty=" + req.orderQuantity())
                .build());

        log.info("[ORDER] 주문 정정 완료 — orderId={}", orderId);
        return OrderResponse.from(order);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private BigDecimal resolveFillPrice(PlaceOrderRequest req, Instrument instrument) {
        if (req.orderPrice() != null && req.orderPrice().compareTo(BigDecimal.ZERO) > 0) {
            return req.orderPrice();
        }
        // MARKET 주문이고 orderPrice가 null인 경우 — 클라이언트가 가격을 보내지 않으면 예외
        throw new BusinessException(OrderErrorCode.INVALID_ORDER_PRICE);
    }

    private String resolveIdempotencyKey(String clientKey) {
        if (clientKey != null && !clientKey.isBlank()) {
            return clientKey;
        }
        return UUID.randomUUID().toString();
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FMT);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "ORD" + timestamp + random;
    }

    private String generateExecutionNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FMT);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "EXE" + timestamp + random;
    }

    private void validateOwnership(Long userId, Order order) {
        if (!order.getAccount().getUser().getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }
}
