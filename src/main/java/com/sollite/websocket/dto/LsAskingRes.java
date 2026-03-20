package com.sollite.websocket.dto;

import java.util.List;

public record LsAskingRes(
        LsAskingHeader header,
        LsAskingBody body
) {
    public record LsAskingHeader(
            String tr_cd
    ) {}

    public record LsAskingBody(
            String hotime,
            String shcode,
            String volume,
            String totofferrem,
            String totbidrem,
            String offerho1,  String offerho2,  String offerho3,  String offerho4,  String offerho5,
            String offerho6,  String offerho7,  String offerho8,  String offerho9,  String offerho10,
            String offerrem1, String offerrem2, String offerrem3, String offerrem4, String offerrem5,
            String offerrem6, String offerrem7, String offerrem8, String offerrem9, String offerrem10,
            String bidho1,  String bidho2,  String bidho3,  String bidho4,  String bidho5,
            String bidho6,  String bidho7,  String bidho8,  String bidho9,  String bidho10,
            String bidrem1, String bidrem2, String bidrem3, String bidrem4, String bidrem5,
            String bidrem6, String bidrem7, String bidrem8, String bidrem9, String bidrem10
    ) {
        public long parsedTotOfferRem() { return parse(totofferrem()); }
        public long parsedTotBidRem()   { return parse(totbidrem()); }

        public List<AskingResponse.OrderEntry> toAsks() {
            return List.of(
                    new AskingResponse.OrderEntry(parse(offerho1()),  parse(offerrem1())),
                    new AskingResponse.OrderEntry(parse(offerho2()),  parse(offerrem2())),
                    new AskingResponse.OrderEntry(parse(offerho3()),  parse(offerrem3())),
                    new AskingResponse.OrderEntry(parse(offerho4()),  parse(offerrem4())),
                    new AskingResponse.OrderEntry(parse(offerho5()),  parse(offerrem5())),
                    new AskingResponse.OrderEntry(parse(offerho6()),  parse(offerrem6())),
                    new AskingResponse.OrderEntry(parse(offerho7()),  parse(offerrem7())),
                    new AskingResponse.OrderEntry(parse(offerho8()),  parse(offerrem8())),
                    new AskingResponse.OrderEntry(parse(offerho9()),  parse(offerrem9())),
                    new AskingResponse.OrderEntry(parse(offerho10()), parse(offerrem10()))
            );
        }

        public List<AskingResponse.OrderEntry> toBids() {
            return List.of(
                    new AskingResponse.OrderEntry(parse(bidho1()),  parse(bidrem1())),
                    new AskingResponse.OrderEntry(parse(bidho2()),  parse(bidrem2())),
                    new AskingResponse.OrderEntry(parse(bidho3()),  parse(bidrem3())),
                    new AskingResponse.OrderEntry(parse(bidho4()),  parse(bidrem4())),
                    new AskingResponse.OrderEntry(parse(bidho5()),  parse(bidrem5())),
                    new AskingResponse.OrderEntry(parse(bidho6()),  parse(bidrem6())),
                    new AskingResponse.OrderEntry(parse(bidho7()),  parse(bidrem7())),
                    new AskingResponse.OrderEntry(parse(bidho8()),  parse(bidrem8())),
                    new AskingResponse.OrderEntry(parse(bidho9()),  parse(bidrem9())),
                    new AskingResponse.OrderEntry(parse(bidho10()), parse(bidrem10()))
            );
        }

        private long parse(String value) {
            if (value == null || value.isBlank()) return 0L;
            try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return 0L; }
        }
    }
}