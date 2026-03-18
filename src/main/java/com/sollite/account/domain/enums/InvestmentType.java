package com.sollite.account.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvestmentType {
    CONSERVATIVE("안정형"),
    CONSERVATIVE_GROWTH("안정추구형"),
    BALANCED("위험중립형"),
    AGGRESSIVE_GROWTH("적극투자형"),
    AGGRESSIVE("공격투자형");

    private final String displayName;
}
