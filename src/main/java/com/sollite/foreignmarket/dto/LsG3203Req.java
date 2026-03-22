package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LsG3203Req(
        G3203InBlock g3203InBlock
) {
    public record G3203InBlock(
            String delaygb,
            String keysymbol,
            String exchcd,
            String symbol,
            int ncnt,
            int qrycnt,
            @JsonProperty("comp_yn") String compYn,
            String sdate,
            String edate,
            @JsonProperty("cts_date") String ctsDate,
            @JsonProperty("cts_time") String ctsTime
    ) {}
}
