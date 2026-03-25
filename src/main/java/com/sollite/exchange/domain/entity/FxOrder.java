package com.sollite.exchange.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.exchange.domain.enums.FxOrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fx_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fx_order_id")
    private Long fxOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @Column(name = "fx_order_no", nullable = false, length = 40)
    private String fxOrderNo;

    @Column(name = "request_channel", nullable = false, length = 20)
    private String requestChannel;

    @Column(name = "from_currency_code", nullable = false, length = 3)
    private String fromCurrencyCode;

    @Column(name = "to_currency_code", nullable = false, length = 3)
    private String toCurrencyCode;

    @Column(name = "request_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestAmount;

    @Column(name = "applied_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal appliedRate;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "receive_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal receiveAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "fx_order_status", nullable = false, length = 20)
    private FxOrderStatus fxOrderStatus;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public FxOrder(Account account, SimulationRound simulationRound, String fxOrderNo,
                   String requestChannel, String fromCurrencyCode, String toCurrencyCode,
                   BigDecimal requestAmount, BigDecimal appliedRate, BigDecimal feeAmount,
                   BigDecimal receiveAmount, FxOrderStatus fxOrderStatus,
                   LocalDateTime requestedAt, LocalDateTime completedAt) {
        this.account = account;
        this.simulationRound = simulationRound;
        this.fxOrderNo = fxOrderNo;
        this.requestChannel = requestChannel;
        this.fromCurrencyCode = fromCurrencyCode;
        this.toCurrencyCode = toCurrencyCode;
        this.requestAmount = requestAmount;
        this.appliedRate = appliedRate;
        this.feeAmount = feeAmount;
        this.receiveAmount = receiveAmount;
        this.fxOrderStatus = fxOrderStatus;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
    }
}
