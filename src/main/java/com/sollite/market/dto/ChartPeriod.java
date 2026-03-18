package com.sollite.market.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChartPeriod {
    DAILY(2),
    WEEKLY(3),
    MONTHLY(4),
    YEARLY(5);

    private final int gubun;
}
