package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LsG3203Res(
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
        @JsonProperty("g3203OutBlock")
        G3203OutBlock g3203OutBlock,
        @JsonProperty("g3203OutBlock1")
        List<G3203OutBlock1> g3203OutBlock1
) {
    public record G3203OutBlock(
            @JsonProperty("delaygb") String delaygb,
            @JsonProperty("keysymbol") String keysymbol,
            @JsonProperty("exchcd") String exchcd,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("cts_date") String ctsDate,
            @JsonProperty("cts_time") String ctsTime,
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
            @JsonProperty("s_time") String sTime,
            @JsonProperty("e_time") String eTime,
            @JsonProperty("timediff") String timediff
    ) {}

    public record G3203OutBlock1(
            @JsonProperty("date") String date,
            @JsonProperty("loctime") String loctime,
            @JsonProperty("open") String open,
            @JsonProperty("high") String high,
            @JsonProperty("low") String low,
            @JsonProperty("close") String close,
            @JsonProperty("exevol") String exevol,
            @JsonProperty("amount") String amount
    ) {}
}
