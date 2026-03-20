package com.sollite.market.dto;

import java.util.List;

public record LsOrderbookUnifiedRes(
        String rsp_cd,
        String rsp_msg,
        LsOrderbookUnifiedBlock t8450OutBlock
) {
    public record LsOrderbookUnifiedBlock(
            String hname,
            int price,
            String sign,
            int change,
            double diff,
            long volume,
            int jnilclose,
            // KRX 호가 (offerho/bidho는 KRX·NXT 공통 가격)
            int offerho1, int offerho2, int offerho3, int offerho4, int offerho5,
            int offerho6, int offerho7, int offerho8, int offerho9, int offerho10,
            int bidho1, int bidho2, int bidho3, int bidho4, int bidho5,
            int bidho6, int bidho7, int bidho8, int bidho9, int bidho10,
            // KRX 잔량
            long offerrem1, long offerrem2, long offerrem3, long offerrem4, long offerrem5,
            long offerrem6, long offerrem7, long offerrem8, long offerrem9, long offerrem10,
            long bidrem1, long bidrem2, long bidrem3, long bidrem4, long bidrem5,
            long bidrem6, long bidrem7, long bidrem8, long bidrem9, long bidrem10,
            long offer,
            long bid,
            // NXT 잔량
            long nxt_offerrem1, long nxt_offerrem2, long nxt_offerrem3, long nxt_offerrem4, long nxt_offerrem5,
            long nxt_offerrem6, long nxt_offerrem7, long nxt_offerrem8, long nxt_offerrem9, long nxt_offerrem10,
            long nxt_bidrem1, long nxt_bidrem2, long nxt_bidrem3, long nxt_bidrem4, long nxt_bidrem5,
            long nxt_bidrem6, long nxt_bidrem7, long nxt_bidrem8, long nxt_bidrem9, long nxt_bidrem10,
            long nxt_offer,
            long nxt_bid,
            // 통합 잔량 (KRX + NXT)
            long unx_offerrem1, long unx_offerrem2, long unx_offerrem3, long unx_offerrem4, long unx_offerrem5,
            long unx_offerrem6, long unx_offerrem7, long unx_offerrem8, long unx_offerrem9, long unx_offerrem10,
            long unx_bidrem1, long unx_bidrem2, long unx_bidrem3, long unx_bidrem4, long unx_bidrem5,
            long unx_bidrem6, long unx_bidrem7, long unx_bidrem8, long unx_bidrem9, long unx_bidrem10,
            long unx_offer,
            long unx_bid,
            // 공통
            String hotime,
            int yeprice,
            long yevolume,
            String yesign,
            int yechange,
            double yediff,
            long tmoffer,
            long tmbid,
            String ho_status,
            String shcode,
            int uplmtprice,
            int dnlmtprice,
            int open,
            int high,
            int low
    ) {
        public List<OrderbookResponse.OrderEntry> toUnifiedAsks() {
            return List.of(
                    new OrderbookResponse.OrderEntry(offerho1, unx_offerrem1),
                    new OrderbookResponse.OrderEntry(offerho2, unx_offerrem2),
                    new OrderbookResponse.OrderEntry(offerho3, unx_offerrem3),
                    new OrderbookResponse.OrderEntry(offerho4, unx_offerrem4),
                    new OrderbookResponse.OrderEntry(offerho5, unx_offerrem5),
                    new OrderbookResponse.OrderEntry(offerho6, unx_offerrem6),
                    new OrderbookResponse.OrderEntry(offerho7, unx_offerrem7),
                    new OrderbookResponse.OrderEntry(offerho8, unx_offerrem8),
                    new OrderbookResponse.OrderEntry(offerho9, unx_offerrem9),
                    new OrderbookResponse.OrderEntry(offerho10, unx_offerrem10)
            );
        }

        public List<OrderbookResponse.OrderEntry> toUnifiedBids() {
            return List.of(
                    new OrderbookResponse.OrderEntry(bidho1, unx_bidrem1),
                    new OrderbookResponse.OrderEntry(bidho2, unx_bidrem2),
                    new OrderbookResponse.OrderEntry(bidho3, unx_bidrem3),
                    new OrderbookResponse.OrderEntry(bidho4, unx_bidrem4),
                    new OrderbookResponse.OrderEntry(bidho5, unx_bidrem5),
                    new OrderbookResponse.OrderEntry(bidho6, unx_bidrem6),
                    new OrderbookResponse.OrderEntry(bidho7, unx_bidrem7),
                    new OrderbookResponse.OrderEntry(bidho8, unx_bidrem8),
                    new OrderbookResponse.OrderEntry(bidho9, unx_bidrem9),
                    new OrderbookResponse.OrderEntry(bidho10, unx_bidrem10)
            );
        }
    }
}
