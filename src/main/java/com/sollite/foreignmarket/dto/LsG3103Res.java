package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LsG3103Res(
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
        @JsonProperty("g3103OutBlock")
        G3103OutBlock g3103OutBlock,
        @JsonProperty("g3103OutBlock1")
        List<G3103OutBlock1> g3103OutBlock1
) {
    public record G3103OutBlock(
            @JsonProperty("delaygb") String delaygb,
            @JsonProperty("keysymbol") String keysymbol,
            @JsonProperty("exchcd") String exchcd,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("gubun") String gubun,
            @JsonProperty("date") String date
    ) {}

    public record G3103OutBlock1(
            @JsonProperty("chedate") String chedate,     // 영업일자
            @JsonProperty("price") String price,         // 현재가
            @JsonProperty("sign") String sign,           // 전일대비구분
            @JsonProperty("diff") String diff,           // 전일대비
            @JsonProperty("rate") String rate,           // 등락률
            @JsonProperty("volume") String volume,       // 누적거래량
            @JsonProperty("open") String open,           // 시가
            @JsonProperty("high") String high,           // 고가
            @JsonProperty("low") String low,             // 저가
            @JsonProperty("floatpoint") String floatpoint
    ) {}
}
