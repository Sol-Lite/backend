package com.sollite.account.domain.entity;

import com.sollite.account.domain.enums.AccountStatus;
import com.sollite.account.domain.enums.InvestmentType;
import com.sollite.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_no", nullable = false, unique = true, length = 30)
    private String accountNo;

    @Column(name = "account_name", length = 100)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "account_pin_hash", nullable = false)
    private String accountPinHash;

    @Column(name = "base_currency_code", nullable = false, length = 3)
    private String baseCurrencyCode = "KRW";

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_tendency", length = 20)
    private InvestmentType investmentTendency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Account(User user, String accountNo, String accountName, String accountPinHash, InvestmentType investmentTendency) {
        this.user = user;
        this.accountNo = accountNo;
        this.accountName = accountName;
        this.accountPinHash = accountPinHash;
        this.investmentTendency = investmentTendency;
    }

    public void changePin(String newPinHash) {
        this.accountPinHash = newPinHash;
    }

    public void close() {
        this.accountStatus = AccountStatus.CLOSED;
    }

    public boolean isActive() {
        return this.accountStatus == AccountStatus.ACTIVE;
    }
}
