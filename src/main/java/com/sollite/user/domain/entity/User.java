package com.sollite.user.domain.entity;

import com.sollite.user.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String emailVerifiedYn = "N";

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "login_fail_count", nullable = false)
    private Integer loginFailCount = 0;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "default_market", length = 10)
    private String defaultMarket = "DOMESTIC";

    @Column(length = 20)
    private String theme = "LIGHT";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public User(String email, String passwordHash, String name, String phone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
    }

    public void verifyEmail() {
        this.emailVerifiedYn = "Y";
        this.emailVerifiedAt = LocalDateTime.now();
    }

    public void incrementLoginFailCount() {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) {
            this.status = UserStatus.LOCKED;
            this.lockedAt = LocalDateTime.now();
        }
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
        this.status = UserStatus.ACTIVE;
        this.lockedAt = null;
    }

    public void unlock() {
        this.status = UserStatus.ACTIVE;
        this.loginFailCount = 0;
        this.lockedAt = null;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateProfile(String name, String phone) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }

    public boolean isEmailVerified() {
        return "Y".equals(this.emailVerifiedYn);
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean isLocked() {
        return this.status == UserStatus.LOCKED;
    }

    public boolean isLockExpired() {
        return this.status == UserStatus.LOCKED
                && this.lockedAt != null
                && this.lockedAt.isBefore(LocalDateTime.now().minusMinutes(10));
    }
}
