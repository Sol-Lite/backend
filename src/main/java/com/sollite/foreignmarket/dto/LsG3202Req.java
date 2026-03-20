package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LsG3202Req(
        G3202InBlock g3202InBlock
) {
    public record G3202InBlock(
            String delaygb,
            String keysymbol,
            String exchcd,
            String symbol,
            int ncnt,
            int qrycnt,
            @JsonProperty("comp_yn") String compYn,
            String sdate,
            String edate,
            @JsonProperty("cts_seq") String ctsSeq
    ) {}
}
