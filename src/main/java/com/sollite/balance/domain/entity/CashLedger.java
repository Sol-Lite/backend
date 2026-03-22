package com.sollite.balance.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.balance.domain.enums.CashEntryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_ledger_id")
    private Long cashLedgerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private CashEntryType entryType;

    @Column(name = "amount_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDelta;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CashLedger(Account account, SimulationRound simulationRound, String currencyCode,
                      CashEntryType entryType, BigDecimal amountDelta, BigDecimal balanceAfter,
                      String referenceType, String referenceId) {
        this.account = account;
        this.simulationRound = simulationRound;
        this.currencyCode = currencyCode;
        this.entryType = entryType;
        this.amountDelta = amountDelta;
        this.balanceAfter = balanceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }
}
