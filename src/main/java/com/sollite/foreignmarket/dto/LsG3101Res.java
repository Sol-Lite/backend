package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LsG3101Res(
        @JsonProperty("rsp_cd")
        String rspCd,
        @JsonProperty("rsp_msg")
        String rspMsg,
        @JsonProperty("tr_cd")
        String trCd,
        @JsonProperty("tr_cont")
        String trCont,
        @JsonProperty("tr_cont_key")
        String trContKey,
        @JsonProperty("g3101OutBlock")
        G3101OutBlock g3101OutBlock
) {
    public record G3101OutBlock(
            @JsonProperty("delaygb")
            String delaygb,          // 지연구분
            @JsonProperty("keysymbol")
            String keysymbol,        // KEY종목코드
            @JsonProperty("exchcd")
            String exchcd,           // 거래소코드
            @JsonProperty("exchange")
            String exchange,         // 거래소ID
            @JsonProperty("suspend")
            String suspend,          // 거래상태
            @JsonProperty("sellonly")
            String sellonly,         // 매매구분
            @JsonProperty("symbol")
            String symbol,           // 종목코드
            @JsonProperty("korname")
            String korname,          // 한글종목명
            @JsonProperty("induname")
            String induname,         // 업종한글명
            @JsonProperty("low52p")
            String low52p,           // 52주최저가
            @JsonProperty("floatpoint")
            String floatpoint,       // 소숫점자릿수
            @JsonProperty("currency")
            String currency,         // 외환코드
            @JsonProperty("price")
            String price,            // 현재가
            @JsonProperty("sign")
            String sign,             // 전일대비구분
            @JsonProperty("diff")
            String diff,             // 전일대비
            @JsonProperty("rate")
            String rate,             // 등락률
            @JsonProperty("volume")
            String volume,           // 거래량
            @JsonProperty("amount")
            String amount,           // 거래대금
            @JsonProperty("high52p")
            String high52p,          // 52주최고가
            @JsonProperty("uplimit")
            String uplimit,          // 상한가
            @JsonProperty("dnlimit")
            String dnlimit,          // 하한가
            @JsonProperty("open")
            String open,             // 시가
            @JsonProperty("high")
            String high,             // 고가
            @JsonProperty("low")
            String low,              // 저가
            @JsonProperty("perv")
            String perv,             // PER
            @JsonProperty("epsv")
            String epsv              // EPS
    ) {}
}
