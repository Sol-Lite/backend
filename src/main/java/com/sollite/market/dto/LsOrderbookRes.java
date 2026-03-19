package com.sollite.market.dto;

import java.util.List;

public record LsOrderbookRes(
        String rsp_cd,
        String rsp_msg,
        LsOrderbookBlock t1101OutBlock
) {
    public record LsOrderbookBlock(
            String hotime,
            int offerho1, int offerho2, int offerho3, int offerho4, int offerho5,
            int offerho6, int offerho7, int offerho8, int offerho9, int offerho10,
            long offerrem1, long offerrem2, long offerrem3, long offerrem4, long offerrem5,
            long offerrem6, long offerrem7, long offerrem8, long offerrem9, long offerrem10,
            int bidho1, int bidho2, int bidho3, int bidho4, int bidho5,
            int bidho6, int bidho7, int bidho8, int bidho9, int bidho10,
            long bidrem1, long bidrem2, long bidrem3, long bidrem4, long bidrem5,
            long bidrem6, long bidrem7, long bidrem8, long bidrem9, long bidrem10,
            long offer,
            long bid,
            int yeprice,
            long yevolume,
            String yediff
    ) {
        public List<OrderbookResponse.OrderEntry> toAsks() {
            return List.of(
                    new OrderbookResponse.OrderEntry(offerho1(), offerrem1()),
                    new OrderbookResponse.OrderEntry(offerho2(), offerrem2()),
                    new OrderbookResponse.OrderEntry(offerho3(), offerrem3()),
                    new OrderbookResponse.OrderEntry(offerho4(), offerrem4()),
                    new OrderbookResponse.OrderEntry(offerho5(), offerrem5()),
                    new OrderbookResponse.OrderEntry(offerho6(), offerrem6()),
                    new OrderbookResponse.OrderEntry(offerho7(), offerrem7()),
                    new OrderbookResponse.OrderEntry(offerho8(), offerrem8()),
                    new OrderbookResponse.OrderEntry(offerho9(), offerrem9()),
                    new OrderbookResponse.OrderEntry(offerho10(), offerrem10())
            );
        }

        public List<OrderbookResponse.OrderEntry> toBids() {
            return List.of(
                    new OrderbookResponse.OrderEntry(bidho1(), bidrem1()),
                    new OrderbookResponse.OrderEntry(bidho2(), bidrem2()),
                    new OrderbookResponse.OrderEntry(bidho3(), bidrem3()),
                    new OrderbookResponse.OrderEntry(bidho4(), bidrem4()),
                    new OrderbookResponse.OrderEntry(bidho5(), bidrem5()),
                    new OrderbookResponse.OrderEntry(bidho6(), bidrem6()),
                    new OrderbookResponse.OrderEntry(bidho7(), bidrem7()),
                    new OrderbookResponse.OrderEntry(bidho8(), bidrem8()),
                    new OrderbookResponse.OrderEntry(bidho9(), bidrem9()),
                    new OrderbookResponse.OrderEntry(bidho10(), bidrem10())
            );
        }
    }
}
