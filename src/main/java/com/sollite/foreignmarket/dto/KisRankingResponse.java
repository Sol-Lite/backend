package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisRankingResponse(
        String rt_cd,
        String msg_cd,
        String msg1,
        Output1 output1,
        List<Output2> output2
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output1(String zdiv, String stat, String nrec) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output2(
            String rsym,
            String excd,
            String symb,
            @JsonAlias("knam") String name,   // 거래량/거래대금/상승하락/시총: name, 급등락: knam
            String last,
            String sign,
            String diff,
            String rate,
            String tvol,
            String tamt,                       // 거래대금
            String a_tamt,                     // 평균거래대금 (거래대금 순위)
            String a_tvol,                     // 평균거래량 (거래량 순위)
            String tomv,                       // 시가총액 (시가총액 순위)
            String grav,                       // 시장비중 (시가총액 순위)
            String rank,
            @JsonAlias("enam") String ename,  // 거래량/거래대금/상승하락/시총: ename, 급등락: enam
            String e_ordyn
    ) {}
}
