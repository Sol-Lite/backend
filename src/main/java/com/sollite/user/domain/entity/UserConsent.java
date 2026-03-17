package com.sollite.user.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "service_terms_agreed_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String serviceTermsAgreedYn;

    @Column(name = "privacy_terms_agreed_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String privacyTermsAgreedYn;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    public static UserConsent of(User user, boolean serviceTermsAgreed, boolean privacyTermsAgreed) {
        UserConsent consent = new UserConsent();
        consent.user = user;
        consent.serviceTermsAgreedYn = serviceTermsAgreed ? "Y" : "N";
        consent.privacyTermsAgreedYn = privacyTermsAgreed ? "Y" : "N";
        consent.termsAgreedAt = LocalDateTime.now();
        return consent;
    }
}
