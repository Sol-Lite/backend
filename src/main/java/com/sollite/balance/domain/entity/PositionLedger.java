package com.sollite.balance.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.balance.domain.enums.PositionEntryType;
import com.sollite.market.domain.entity.Instrument;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "position_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_ledger_id")
    private Long positionLedgerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private PositionEntryType entryType;

    @Column(name = "quantity_delta", nullable = false)
    private Long quantityDelta;

    @Column(name = "holding_quantity_after", nullable = false)
    private Long holdingQuantityAfter;

    @Column(name = "avg_buy_price_after", precision = 19, scale = 4)
    private BigDecimal avgBuyPriceAfter;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PositionLedger(Account account, SimulationRound simulationRound, Instrument instrument,
                          PositionEntryType entryType, Long quantityDelta, Long holdingQuantityAfter,
                          BigDecimal avgBuyPriceAfter, String referenceType, String referenceId) {
        this.account = account;
        this.simulationRound = simulationRound;
        this.instrument = instrument;
        this.entryType = entryType;
        this.quantityDelta = quantityDelta;
        this.holdingQuantityAfter = holdingQuantityAfter;
        this.avgBuyPriceAfter = avgBuyPriceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }
}
