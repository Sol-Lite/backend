package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LsG3202Res(
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
        @JsonProperty("g3202OutBlock")
        G3202OutBlock g3202OutBlock,
        @JsonProperty("g3202OutBlock1")
        List<G3202OutBlock1> g3202OutBlock1
) {
    public record G3202OutBlock(
            @JsonProperty("delaygb") String delaygb,
            @JsonProperty("keysymbol") String keysymbol,
            @JsonProperty("exchcd") String exchcd,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("cts_seq") String ctsSeq,
            @JsonProperty("rec_count") String recCount,
            @JsonProperty("preopen") String preopen,      // 전일시가
            @JsonProperty("prehigh") String prehigh,      // 전일고가
            @JsonProperty("prelow") String prelow,        // 전일저가
            @JsonProperty("preclose") String preclose,    // 전일종가
            @JsonProperty("prevolume") String prevolume,  // 전일거래량
            @JsonProperty("open") String open,            // 당일시가
            @JsonProperty("high") String high,            // 당일고가
            @JsonProperty("low") String low,              // 당일저가
            @JsonProperty("close") String close,          // 당일종가
            @JsonProperty("s_time") String sTime,         // 장시작시간
            @JsonProperty("e_time") String eTime,         // 장종료시간
            @JsonProperty("last_count") String lastCount, // 마지막Tick건수
            @JsonProperty("timediff") String timediff,
            @JsonProperty("prtt_rate") String prttRate    // 수정비율
    ) {}

    public record G3202OutBlock1(
            @JsonProperty("date") String date,            // 날짜
            @JsonProperty("loctime") String loctime,      // 현지시간
            @JsonProperty("open") String open,            // 시가
            @JsonProperty("high") String high,            // 고가
            @JsonProperty("low") String low,              // 저가
            @JsonProperty("close") String close,          // 종가
            @JsonProperty("exevol") String exevol,        // 체결량
            @JsonProperty("jongchk") String jongchk,      // 수정구분
            @JsonProperty("pricechk") String pricechk,    // 수정주가반영항목
            @JsonProperty("sign") String sign             // 종가등락구분
    ) {}
}
