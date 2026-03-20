package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LsG3204Res(
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
        @JsonProperty("g3204OutBlock")
        G3204OutBlock g3204OutBlock,
        @JsonProperty("g3204OutBlock1")
        List<G3204OutBlock1> g3204OutBlock1
) {
    public record G3204OutBlock(
            @JsonProperty("delaygb") String delaygb,
            @JsonProperty("keysymbol") String keysymbol,
            @JsonProperty("exchcd") String exchcd,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("cts_date") String ctsDate,
            @JsonProperty("cts_info") String ctsInfo,
            @JsonProperty("rec_count") String recCount,
            @JsonProperty("preopen") String preopen,
            @JsonProperty("prehigh") String prehigh,
            @JsonProperty("prelow") String prelow,
            @JsonProperty("preclose") String preclose,
            @JsonProperty("prevolume") String prevolume,
            @JsonProperty("open") String open,
            @JsonProperty("high") String high,
            @JsonProperty("low") String low,
            @JsonProperty("close") String close,
            @JsonProperty("uplimit") String uplimit,
            @JsonProperty("dnlimit") String dnlimit,
            @JsonProperty("s_time") String sTime,
            @JsonProperty("e_time") String eTime,
            @JsonProperty("dshmin") String dshmin
    ) {}

    public record G3204OutBlock1(
            @JsonProperty("date") String date,
            @JsonProperty("open") String open,
            @JsonProperty("high") String high,
            @JsonProperty("low") String low,
            @JsonProperty("close") String close,
            @JsonProperty("volume") String volume,
            @JsonProperty("amount") String amount,
            @JsonProperty("jongchk") String jongchk,
            @JsonProperty("prtt_rate") String prttRate,
            @JsonProperty("pricechk") String pricechk,
            @JsonProperty("ratevalue") String ratevalue,
            @JsonProperty("sign") String sign
    ) {}
}
