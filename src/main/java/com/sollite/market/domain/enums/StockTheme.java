package com.sollite.market.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StockTheme {

    SEMICONDUCTOR("반도체"),
    BATTERY("2차전지/배터리"),
    AUTOMOTIVE("자동차/전장"),
    IT_PLATFORM("IT 플랫폼"),
    ENERGY_CHEMICAL("에너지/화학"),
    FINANCE("금융"),
    SHIPBUILDING("조선"),
    DEFENSE("방산"),
    MANUFACTURING("인프라 & 산업재"),
    ROBOT("로봇");

    private final String displayName;
}
