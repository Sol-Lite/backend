package com.sollite.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LsT3320Res(
        String rsp_cd,
        String rsp_msg,
        LsT3320OutBlock t3320OutBlock
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LsT3320OutBlock(
            String company,     // 한글 기업명
            String upgubunnm,   // 업종 구분명
            String marketnm     // 시장 구분명
    ) {}
}
