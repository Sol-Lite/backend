package com.sollite.foreignmarket.dto;

import lombok.Getter;

@Getter
public enum ForeignChartPeriod {
    DAY("1"),       // 일봉
    WEEK("2"),      // 주봉
    MONTH("3"),     // 월봉
    YEAR("4");      // 년봉

    private final String gubun;

    ForeignChartPeriod(String gubun) {
        this.gubun = gubun;
    }
}
