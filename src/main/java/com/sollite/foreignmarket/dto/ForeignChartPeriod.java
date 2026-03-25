package com.sollite.foreignmarket.dto;

import lombok.Getter;

@Getter
public enum ForeignChartPeriod {
    DAY("2"),       // 일봉
    WEEK("3"),      // 주봉
    MONTH("4"),     // 월봉
    YEAR("5");      // 년봉

    private final String gubun;

    ForeignChartPeriod(String gubun) {
        this.gubun = gubun;
    }
}
