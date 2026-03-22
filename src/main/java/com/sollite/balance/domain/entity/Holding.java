package com.sollite.balance.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.market.domain.entity.Instrument;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "holdings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Long holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "holding_quantity", nullable = false)
    private Long holdingQuantity;

    @Column(name = "available_quantity", nullable = false)
    private Long availableQuantity;

    @Column(name = "avg_buy_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgBuyPrice;

    @Column(name = "avg_buy_exchange_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal avgBuyExchangeRate;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Holding(Account account, SimulationRound simulationRound, Instrument instrument,
                   Long holdingQuantity, Long availableQuantity,
                   BigDecimal avgBuyPrice, BigDecimal avgBuyExchangeRate) {
        this.account = account;
        this.simulationRound = simulationRound;
        this.instrument = instrument;
        this.holdingQuantity = holdingQuantity;
        this.availableQuantity = availableQuantity;
        this.avgBuyPrice = avgBuyPrice;
        this.avgBuyExchangeRate = avgBuyExchangeRate != null ? avgBuyExchangeRate : BigDecimal.ONE;
    }

    /** 매도 주문 접수: 가용수량 차감 */
    public void reserveForSell(long quantity) {
        this.availableQuantity -= quantity;
    }

    /** 매도 취소: 가용수량 복원 */
    public void cancelSellReserve(long quantity) {
        this.availableQuantity += quantity;
    }

    /** 매수 체결: 수량 증가 + 평단가 재계산 */
    public void addBuyFill(long quantity, BigDecimal fillPrice) {
        BigDecimal prevTotal = this.avgBuyPrice.multiply(BigDecimal.valueOf(this.holdingQuantity));
        BigDecimal newTotal = fillPrice.multiply(BigDecimal.valueOf(quantity));
        long newQty = this.holdingQuantity + quantity;
        this.avgBuyPrice = prevTotal.add(newTotal)
                .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
        this.holdingQuantity = newQty;
        this.availableQuantity += quantity;
    }

    /** 매도 체결: holding_quantity 차감 (available은 접수 시 이미 차감됨) */
    public void subtractSellFill(long quantity) {
        this.holdingQuantity -= quantity;
    }
}
