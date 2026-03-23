package com.sollite.order.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.order.domain.enums.OrderChannel;
import com.sollite.order.domain.enums.OrderKind;
import com.sollite.order.domain.enums.OrderSide;
import com.sollite.order.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_channel", nullable = false, length = 20)
    private OrderChannel orderChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_side", nullable = false, length = 10)
    private OrderSide orderSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_kind", nullable = false, length = 20)
    private OrderKind orderKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Column(name = "order_price", precision = 19, scale = 4)
    private BigDecimal orderPrice;

    @Column(name = "order_quantity", nullable = false)
    private Long orderQuantity;

    @Column(name = "filled_quantity", nullable = false)
    private Long filledQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Long remainingQuantity;

    @Column(name = "reserved_amount", precision = 19, scale = 4)
    private BigDecimal reservedAmount;

    @Column(name = "original_order_id")
    private Long originalOrderId;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Order(Account account, SimulationRound simulationRound, Instrument instrument,
                 String orderNo, OrderChannel orderChannel, OrderSide orderSide,
                 OrderKind orderKind, BigDecimal orderPrice, Long orderQuantity,
                 BigDecimal reservedAmount, Long originalOrderId, String idempotencyKey) {
        this.account = account;
        this.simulationRound = simulationRound;
        this.instrument = instrument;
        this.orderNo = orderNo;
        this.orderChannel = orderChannel;
        this.orderSide = orderSide;
        this.orderKind = orderKind;
        this.orderStatus = OrderStatus.PENDING;
        this.orderPrice = orderPrice;
        this.orderQuantity = orderQuantity;
        this.filledQuantity = 0L;
        this.remainingQuantity = orderQuantity;
        this.reservedAmount = reservedAmount;
        this.originalOrderId = originalOrderId;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = LocalDateTime.now();
    }

    public void fill() {
        this.orderStatus = OrderStatus.FILLED;
        this.filledQuantity = this.orderQuantity;
        this.remainingQuantity = 0L;
        this.submittedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.orderStatus = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void reject() {
        this.orderStatus = OrderStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
    }

    public void amend(BigDecimal newPrice, Long newQuantity, BigDecimal newReservedAmount) {
        this.orderPrice = newPrice;
        this.orderQuantity = newQuantity;
        this.remainingQuantity = newQuantity;
        this.reservedAmount = newReservedAmount;
    }

    public boolean isPending() {
        return this.orderStatus == OrderStatus.PENDING;
    }
}
