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
import com.sollite.balance.domain.enums.CashEntryType;
import com.sollite.balance.domain.repository.CashBalanceRepository;
import com.sollite.balance.domain.repository.CashLedgerRepository;
import com.sollite.balance.domain.repository.HoldingRepository;
import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.entity.OrderEvent;
import com.sollite.order.domain.enums.OrderEventType;
import com.sollite.order.domain.enums.OrderKind;
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
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import com.sollite.market.service.MarketService;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final OrderExecutionService orderExecutionService;
    private final OrderMatchingService orderMatchingService;
    private final OrderWaitingSubscriptionManager subscriptionManager;
    private final LsBrokerService lsBrokerService;
    private final MarketService marketService;
    private final ForeignStockMarketService foreignStockMarketService;
    private final ObjectMapper objectMapper;

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

        // 2. 종목 조회 (stockCode + marketType → Instrument)
        Instrument instrument = instrumentRepository
                .findByStockCodeAndMarketType(req.stockCode(), req.marketType())
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

        // 5. CashLedger: BUY_RESERVE
        cashLedgerRepository.save(CashLedger.builder()
                .account(account)
                .simulationRound(round)
                .currencyCode(currencyCode)
                .entryType(CashEntryType.BUY_RESERVE)
                .amountDelta(cost.negate())
                .balanceAfter(cashBalance.getAvailableAmount())
                .referenceType("ORDER")
                .referenceId(String.valueOf(order.getOrderId()))
                .build());

        // 6. OrderEvent: PLACED
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .eventType(OrderEventType.PLACED)
                .beforeStatus(null)
                .afterStatus(OrderStatus.PENDING)
                .build());

        log.info("[ORDER] 매수 주문 접수 — orderId={}, kind={}, instrument={}, qty={}, price={}",
                order.getOrderId(), req.orderKind(), instrument.getStockCode(), req.orderQuantity(), fillPrice);

        // 7. LIMIT → PENDING 유지 + 구독 hold + 즉시 매칭 검사
        //    MARKET / CURRENT_PRICE → 즉시 체결
        if (req.orderKind() == OrderKind.LIMIT) {
            subscriptionManager.hold(order.getOrderId(), instrument.getMarketType(), instrument.getStockCode());
            BigDecimal lastPrice = resolveLastPrice(instrument);
            orderMatchingService.tryImmediateMatch(order, lastPrice);
        } else {
            orderExecutionService.execute(order.getOrderId(), fillPrice);
        }

        return OrderResponse.from(order);
    }

    // -----------------------------------------------------------------------
    // 매도 처리
    // -----------------------------------------------------------------------

    private OrderResponse processSellOrder(Account account, SimulationRound round, Instrument instrument,
                                           PlaceOrderRequest req, String idempotencyKey, BigDecimal fillPrice) {
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

        log.info("[ORDER] 매도 주문 접수 — orderId={}, kind={}, instrument={}, qty={}, price={}",
                order.getOrderId(), req.orderKind(), instrument.getStockCode(), req.orderQuantity(), fillPrice);

        // 6. LIMIT → PENDING 유지 + 구독 hold + 즉시 매칭 검사
        //    MARKET / CURRENT_PRICE → 즉시 체결
        if (req.orderKind() == OrderKind.LIMIT) {
            subscriptionManager.hold(order.getOrderId(), instrument.getMarketType(), instrument.getStockCode());
            BigDecimal lastPrice = resolveLastPrice(instrument);
            orderMatchingService.tryImmediateMatch(order, lastPrice);
        } else {
            orderExecutionService.execute(order.getOrderId(), fillPrice);
        }

        return OrderResponse.from(order);
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
            OrderStatus orderStatus;
            try {
                orderStatus = OrderStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS);
            }
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

        // LIMIT 주문 구독 해제
        if (order.getOrderKind() == OrderKind.LIMIT) {
            subscriptionManager.release(orderId);
        }

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
            try {
                cancelOrder(userId, order.getOrderId());
                count++;
            } catch (BusinessException e) {
                if (e.getErrorCode() == OrderErrorCode.ORDER_NOT_CANCELLABLE) {
                    log.info("[ORDER] 취소 스킵 (이미 체결/취소) — orderId={}", order.getOrderId());
                } else {
                    throw e;
                }
            }
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

        // 정정 후 즉시 매칭 검사 (가격 변경으로 이미 체결 가능할 수 있음)
        if (order.getOrderKind() == OrderKind.LIMIT) {
            BigDecimal lastPrice = resolveLastPrice(instrument);
            orderMatchingService.tryImmediateMatch(order, lastPrice);
        }

        return OrderResponse.from(order);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private BigDecimal resolveFillPrice(PlaceOrderRequest req, Instrument instrument) {
        // LIMIT: 반드시 클라이언트가 가격 지정
        if (req.orderKind() == OrderKind.LIMIT) {
            if (req.orderPrice() == null || req.orderPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_PRICE);
            }
            return req.orderPrice();
        }

        // MARKET / CURRENT_PRICE: 서버가 현재가를 결정한다 (클라이언트 가격 무시)
        BigDecimal currentPrice = fetchCurrentPrice(instrument);
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_PRICE);
        }
        return currentPrice;
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

    /**
     * 마지막 시세 조회 (인메모리 캐시 → REST API 폴백)
     */
    private BigDecimal resolveLastPrice(Instrument instrument) {
        // 1. 인메모리 캐시에서 먼저 시도
        String topic;
        if (List.of("NASDAQ", "NYSE", "AMEX").contains(instrument.getMarketType())) {
            topic = "/topic/foreign/transaction/" + instrument.getStockCode();
        } else {
            topic = "/topic/stock/trade/" + instrument.getStockCode();
        }
        String lastJson = lsBrokerService.getLastValue(topic);
        if (lastJson != null) {
            try {
                var node = objectMapper.readTree(lastJson);
                if (node.has("price")) {
                    return new BigDecimal(node.get("price").asText().replace(",", "").trim());
                }
            } catch (Exception e) {
                log.warn("[ORDER] lastValue 가격 파싱 실패 — topic={}, error={}", topic, e.getMessage());
            }
        }

        // 2. 캐시 미스 → REST API 폴백
        return fetchCurrentPrice(instrument);
    }

    /**
     * LS REST API로 현재가 조회
     */
    private BigDecimal fetchCurrentPrice(Instrument instrument) {
        try {
            if (List.of("NASDAQ", "NYSE", "AMEX").contains(instrument.getMarketType())) {
                String exchcd = resolveExchangeCode(instrument.getExchangeCode());
                var response = foreignStockMarketService.getCurrentPrice(instrument.getStockCode(), exchcd);
                return BigDecimal.valueOf(response.price());
            } else {
                var response = marketService.getCurrentPrice(instrument.getStockCode());
                return BigDecimal.valueOf(response.currentPrice());
            }
        } catch (Exception e) {
            log.warn("[ORDER] REST 현재가 조회 실패 — instrument={}, error={}",
                    instrument.getStockCode(), e.getMessage());
            return null;
        }
    }

    private String resolveExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return "82";
        return switch (exchangeCode.trim().toUpperCase()) {
            case "NAS", "NASDAQ", "82" -> "82";
            case "NYS", "NYSE", "AMS", "AMEX", "81" -> "81";
            default -> {
                log.warn("[ORDER] 알 수 없는 거래소 코드 — exchangeCode={}", exchangeCode);
                yield "82";
            }
        };
    }

    private void validateOwnership(Long userId, Order order) {
        if (!order.getAccount().getUser().getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }
}
