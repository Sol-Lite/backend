package com.sollite.market.dto;

import java.util.List;

public record LsOpinionRes (
        String rsp_cd,
        String rsp_msg,
        List<LsOpinionItem> t3401OutBlock1
) {
    public record LsOpinionItem(
            String date,        // 의견일자
            String tradname,    // 회원사명
            String bopn,        // 투자의견 변경후
            String nopn,        // 투자의견 변경전
            int noga,           // 목표가 변경후
            int boga            // 목표가 변경전
    ) {}


}
