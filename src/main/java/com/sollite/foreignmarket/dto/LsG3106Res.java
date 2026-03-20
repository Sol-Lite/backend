package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LsG3106Res(
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
        @JsonProperty("g3106OutBlock")
        G3106OutBlock g3106OutBlock
) {
    public record G3106OutBlock(
            @JsonProperty("delaygb")
            String delaygb,
            @JsonProperty("keysymbol")
            String keysymbol,
            @JsonProperty("exchcd")
            String exchcd,
            @JsonProperty("symbol")
            String symbol,
            @JsonProperty("korname")
            String korname,
            @JsonProperty("price")
            String price,
            @JsonProperty("floatpoint")
            String floatpoint,
            @JsonProperty("sign")
            String sign,
            @JsonProperty("diff")
            String diff,
            @JsonProperty("rate")
            String rate,
            @JsonProperty("volume")
            String volume,
            @JsonProperty("amount")
            String amount,
            @JsonProperty("jnilclose")
            String jnilclose,    // 전일종가
            @JsonProperty("open")
            String open,
            @JsonProperty("high")
            String high,
            @JsonProperty("low")
            String low,
            @JsonProperty("hotime")
            String hotime,       // 호가수신시간
            // 매도 호가 1~10
            @JsonProperty("offerho1") String offerho1,
            @JsonProperty("offerho2") String offerho2,
            @JsonProperty("offerho3") String offerho3,
            @JsonProperty("offerho4") String offerho4,
            @JsonProperty("offerho5") String offerho5,
            @JsonProperty("offerho6") String offerho6,
            @JsonProperty("offerho7") String offerho7,
            @JsonProperty("offerho8") String offerho8,
            @JsonProperty("offerho9") String offerho9,
            @JsonProperty("offerho10") String offerho10,
            // 매수 호가 1~10
            @JsonProperty("bidho1") String bidho1,
            @JsonProperty("bidho2") String bidho2,
            @JsonProperty("bidho3") String bidho3,
            @JsonProperty("bidho4") String bidho4,
            @JsonProperty("bidho5") String bidho5,
            @JsonProperty("bidho6") String bidho6,
            @JsonProperty("bidho7") String bidho7,
            @JsonProperty("bidho8") String bidho8,
            @JsonProperty("bidho9") String bidho9,
            @JsonProperty("bidho10") String bidho10,
            // 매도 호가 잔량 1~10
            @JsonProperty("offerrem1") String offerrem1,
            @JsonProperty("offerrem2") String offerrem2,
            @JsonProperty("offerrem3") String offerrem3,
            @JsonProperty("offerrem4") String offerrem4,
            @JsonProperty("offerrem5") String offerrem5,
            @JsonProperty("offerrem6") String offerrem6,
            @JsonProperty("offerrem7") String offerrem7,
            @JsonProperty("offerrem8") String offerrem8,
            @JsonProperty("offerrem9") String offerrem9,
            @JsonProperty("offerrem10") String offerrem10,
            // 매수 호가 잔량 1~10
            @JsonProperty("bidrem1") String bidrem1,
            @JsonProperty("bidrem2") String bidrem2,
            @JsonProperty("bidrem3") String bidrem3,
            @JsonProperty("bidrem4") String bidrem4,
            @JsonProperty("bidrem5") String bidrem5,
            @JsonProperty("bidrem6") String bidrem6,
            @JsonProperty("bidrem7") String bidrem7,
            @JsonProperty("bidrem8") String bidrem8,
            @JsonProperty("bidrem9") String bidrem9,
            @JsonProperty("bidrem10") String bidrem10,
            // 호가 합계
            @JsonProperty("offer")
            String offer,        // 매도호가잔량합
            @JsonProperty("bid")
            String bid           // 매수호가잔량합
    ) {
        // 호가 데이터 조회 메서드
        public String getOfferPrice(int level) {
            return switch(level) {
                case 1 -> offerho1; case 2 -> offerho2; case 3 -> offerho3; case 4 -> offerho4;
                case 5 -> offerho5; case 6 -> offerho6; case 7 -> offerho7; case 8 -> offerho8;
                case 9 -> offerho9; case 10 -> offerho10;
                default -> null;
            };
        }

        public String getBidPrice(int level) {
            return switch(level) {
                case 1 -> bidho1; case 2 -> bidho2; case 3 -> bidho3; case 4 -> bidho4;
                case 5 -> bidho5; case 6 -> bidho6; case 7 -> bidho7; case 8 -> bidho8;
                case 9 -> bidho9; case 10 -> bidho10;
                default -> null;
            };
        }

        public String getOfferRemain(int level) {
            return switch(level) {
                case 1 -> offerrem1; case 2 -> offerrem2; case 3 -> offerrem3; case 4 -> offerrem4;
                case 5 -> offerrem5; case 6 -> offerrem6; case 7 -> offerrem7; case 8 -> offerrem8;
                case 9 -> offerrem9; case 10 -> offerrem10;
                default -> null;
            };
        }

        public String getBidRemain(int level) {
            return switch(level) {
                case 1 -> bidrem1; case 2 -> bidrem2; case 3 -> bidrem3; case 4 -> bidrem4;
                case 5 -> bidrem5; case 6 -> bidrem6; case 7 -> bidrem7; case 8 -> bidrem8;
                case 9 -> bidrem9; case 10 -> bidrem10;
                default -> null;
            };
        }
    }
}
