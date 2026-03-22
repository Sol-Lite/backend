package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LsG3204Req(
        G3204InBlock g3204InBlock
) {
    public record G3204InBlock(
            String sujung,
            String delaygb,
            String keysymbol,
            String exchcd,
            String symbol,
            String gubun,
            int qrycnt,
            @JsonProperty("comp_yn") String compYn,
            String sdate,
            String edate,
            @JsonProperty("cts_date") String ctsDate,
            @JsonProperty("cts_info") String ctsInfo
    ) {}
}
