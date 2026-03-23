package com.sollite.order.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.order.domain.enums.OrderSide;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "execution_no", nullable = false, unique = true, length = 40)
    private String executionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_side", nullable = false, length = 10)
    private OrderSide orderSide;

    @Column(name = "execution_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal executionPrice;

    @Column(name = "execution_quantity", nullable = false)
    private Long executionQuantity;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Execution(Order order, Account account, SimulationRound simulationRound,
                     Instrument instrument, String executionNo, OrderSide orderSide,
                     BigDecimal executionPrice, Long executionQuantity,
                     BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal taxAmount,
                     BigDecimal netAmount) {
        this.order = order;
        this.account = account;
        this.simulationRound = simulationRound;
        this.instrument = instrument;
        this.executionNo = executionNo;
        this.orderSide = orderSide;
        this.executionPrice = executionPrice;
        this.executionQuantity = executionQuantity;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.taxAmount = taxAmount;
        this.netAmount = netAmount;
        this.executedAt = LocalDateTime.now();
    }
}
