package com.sollite.balance.domain.entity;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_balance_id")
    private Long cashBalanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_round_id", nullable = false)
    private SimulationRound simulationRound;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "available_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public CashBalance(Account account, SimulationRound simulationRound, String currencyCode,
                       BigDecimal availableAmount, BigDecimal totalAmount) {
        this.account = account;
        this.simulationRound = simulationRound;
        this.currencyCode = currencyCode;
        this.availableAmount = availableAmount;
        this.totalAmount = totalAmount;
    }

    /** 매수 주문 접수: 가용금액 차감 (total 유지) */
    public void reserveForBuy(BigDecimal amount) {
        this.availableAmount = this.availableAmount.subtract(amount);
    }

    /** 매수 취소/거부: 가용금액 복원 */
    public void cancelBuyReserve(BigDecimal amount) {
        this.availableAmount = this.availableAmount.add(amount);
    }

    /** 매수 체결: total 차감 (available은 접수 시 이미 차감됨) */
    public void settleForBuy(BigDecimal amount) {
        this.totalAmount = this.totalAmount.subtract(amount);
    }

    /** 매도 체결: available + total 모두 증가 */
    public void settleForSell(BigDecimal proceeds) {
        this.availableAmount = this.availableAmount.add(proceeds);
        this.totalAmount = this.totalAmount.add(proceeds);
    }

    /** 계좌 폐쇄 전 현금 0원 처리 */
    public void resetToZero() {
        this.availableAmount = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
    }
}
