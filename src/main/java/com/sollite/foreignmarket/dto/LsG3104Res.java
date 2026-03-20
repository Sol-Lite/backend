package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LsG3104Res(
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
        @JsonProperty("g3104OutBlock")
        G3104OutBlock g3104OutBlock
) {
    public record G3104OutBlock(
            @JsonProperty("delaygb") String delaygb,
            @JsonProperty("keysymbol") String keysymbol,
            @JsonProperty("exchcd") String exchcd,
            @JsonProperty("exchange") String exchange,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("korname") String korname,
            @JsonProperty("engname") String engname,
            @JsonProperty("exchange_name") String exchangeName,
            @JsonProperty("nation_name") String nationName,
            @JsonProperty("induname") String induname,
            @JsonProperty("instname") String instname,           // 증권종류
            @JsonProperty("floatpoint") String floatpoint,
            @JsonProperty("currency") String currency,
            @JsonProperty("suspend") String suspend,
            @JsonProperty("sellonly") String sellonly,
            @JsonProperty("share") String share,                // 발행주식수
            @JsonProperty("untprc") String untprc,              // 호가단위
            @JsonProperty("bidlotsize") String bidlotsize,      // 매수주문단위
            @JsonProperty("asklotsize") String asklotsize,      // 매도주문단위
            @JsonProperty("volume") String volume,
            @JsonProperty("amount") String amount,
            @JsonProperty("pcls") String pcls,                  // 전일종가
            @JsonProperty("clos") String clos,                  // 기준가
            @JsonProperty("open") String open,
            @JsonProperty("high") String high,
            @JsonProperty("low") String low,
            @JsonProperty("high52p") String high52p,
            @JsonProperty("low52p") String low52p,
            @JsonProperty("shareprc") String shareprc,          // 시가총액
            @JsonProperty("perv") String perv,
            @JsonProperty("epsv") String epsv,
            @JsonProperty("exrate") String exrate,              // 환율
            @JsonProperty("bidlotsize2") String bidlotsize2,
            @JsonProperty("asklotsize2") String asklotsize2
    ) {}
}
