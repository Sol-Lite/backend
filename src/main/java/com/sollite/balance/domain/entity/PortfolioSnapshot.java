package com.sollite.balance.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(name = "cash_krw", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashKrw;

    @Column(name = "cash_usd", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashUsd;

    @Column(name = "usd_exchange_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal usdExchangeRate;

    @Column(name = "stock_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal stockValue;

    @Column(name = "daily_return", precision = 10, scale = 4)
    private BigDecimal dailyReturn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PortfolioSnapshot(Account account,
                             SimulationRound simulationRound,
                             LocalDate snapshotDate,
                             BigDecimal totalValue,
                             BigDecimal cashKrw,
                             BigDecimal cashUsd,
                             BigDecimal usdExchangeRate,
                             BigDecimal stockValue,
                             BigDecimal dailyReturn) {
        this.account = account;
        this.simulationRound = simulationRound;
        this.snapshotDate = snapshotDate;
        this.totalValue = totalValue;
        this.cashKrw = cashKrw;
        this.cashUsd = cashUsd;
        this.usdExchangeRate = usdExchangeRate;
        this.stockValue = stockValue;
        this.dailyReturn = dailyReturn;
    }

    public void updateMetrics(BigDecimal totalValue,
                              BigDecimal cashKrw,
                              BigDecimal cashUsd,
                              BigDecimal usdExchangeRate,
                              BigDecimal stockValue,
                              BigDecimal dailyReturn) {
        this.totalValue = totalValue;
        this.cashKrw = cashKrw;
        this.cashUsd = cashUsd;
        this.usdExchangeRate = usdExchangeRate;
        this.stockValue = stockValue;
        this.dailyReturn = dailyReturn;
    }
}
