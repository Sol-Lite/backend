package com.sollite.account.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "simulation_round_id")
    private Long simulationRoundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "initial_seed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialSeedAmount;

    @Column(name = "round_end_reason_code", length = 50)
    private String roundEndReasonCode;

    @Column(name = "round_status", nullable = false, length = 20)
    private String roundStatus = "ACTIVE";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SimulationRound(Account account, Integer roundNo, BigDecimal initialSeedAmount) {
        this.account = account;
        this.roundNo = roundNo;
        this.initialSeedAmount = initialSeedAmount;
        this.startedAt = LocalDateTime.now();
    }

    public void close(String reasonCode) {
        this.roundStatus = "CLOSED";
        this.endedAt = LocalDateTime.now();
        this.roundEndReasonCode = reasonCode;
    }
}
