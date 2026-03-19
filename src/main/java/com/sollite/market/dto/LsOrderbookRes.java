package com.sollite.market.dto;

public record LsOrderbookRes(
        String rsp_cd,
        String rsp_msg,
        LsOrderbookBlock t1101OutBlock
) {
    public record LsOrderbookBlock(
            String hotime,
            // 매도호가 1~10
            int offerho1, int offerho2, int offerho3, int offerho4, int offerho5,
            int offerho6, int offerho7, int offerho8, int offerho9, int offerho10,
            // 매도호가수량 1~10
            long offerrem1, long offerrem2, long offerrem3, long offerrem4, long offerrem5,
            long offerrem6, long offerrem7, long offerrem8, long offerrem9, long offerrem10,
            // 매수호가 1~10
            int bidho1, int bidho2, int bidho3, int bidho4, int bidho5,
            int bidho6, int bidho7, int bidho8, int bidho9, int bidho10,
            // 매수호가수량 1~10
            long bidrem1, long bidrem2, long bidrem3, long bidrem4, long bidrem5,
            long bidrem6, long bidrem7, long bidrem8, long bidrem9, long bidrem10,
            // 합계
            long offer,     // 매도호가수량합
            long bid,       // 매수호가수량합
            // 예상체결
            int yeprice,
            long yevolume,
            String yediff
    ) {}
}
