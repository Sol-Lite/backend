package com.sollite.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LsAskingRes(
        LsAskingHeader header,
        LsAskingBody body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LsAskingHeader(
            String tr_cd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LsAskingBody(
            String hotime,
            String shcode,
            String volume,
            @JsonProperty("unt_totofferrem") String untTotofferrem,
            @JsonProperty("unt_totbidrem")   String untTotbidrem,
            String offerho1,  String offerho2,  String offerho3,  String offerho4,  String offerho5,
            String offerho6,  String offerho7,  String offerho8,  String offerho9,  String offerho10,
            @JsonProperty("unt_offerrem1")  String untOfferrem1,
            @JsonProperty("unt_offerrem2")  String untOfferrem2,
            @JsonProperty("unt_offerrem3")  String untOfferrem3,
            @JsonProperty("unt_offerrem4")  String untOfferrem4,
            @JsonProperty("unt_offerrem5")  String untOfferrem5,
            @JsonProperty("unt_offerrem6")  String untOfferrem6,
            @JsonProperty("unt_offerrem7")  String untOfferrem7,
            @JsonProperty("unt_offerrem8")  String untOfferrem8,
            @JsonProperty("unt_offerrem9")  String untOfferrem9,
            @JsonProperty("unt_offerrem10") String untOfferrem10,
            String bidho1,  String bidho2,  String bidho3,  String bidho4,  String bidho5,
            String bidho6,  String bidho7,  String bidho8,  String bidho9,  String bidho10,
            @JsonProperty("unt_bidrem1")  String untBidrem1,
            @JsonProperty("unt_bidrem2")  String untBidrem2,
            @JsonProperty("unt_bidrem3")  String untBidrem3,
            @JsonProperty("unt_bidrem4")  String untBidrem4,
            @JsonProperty("unt_bidrem5")  String untBidrem5,
            @JsonProperty("unt_bidrem6")  String untBidrem6,
            @JsonProperty("unt_bidrem7")  String untBidrem7,
            @JsonProperty("unt_bidrem8")  String untBidrem8,
            @JsonProperty("unt_bidrem9")  String untBidrem9,
            @JsonProperty("unt_bidrem10") String untBidrem10
    ) {
        public long parsedTotOfferRem() { return parse(untTotofferrem()); }
        public long parsedTotBidRem()   { return parse(untTotbidrem()); }

        public List<AskingResponse.OrderEntry> toAsks() {
            return List.of(
                    new AskingResponse.OrderEntry(parse(offerho1()),  parse(untOfferrem1())),
                    new AskingResponse.OrderEntry(parse(offerho2()),  parse(untOfferrem2())),
                    new AskingResponse.OrderEntry(parse(offerho3()),  parse(untOfferrem3())),
                    new AskingResponse.OrderEntry(parse(offerho4()),  parse(untOfferrem4())),
                    new AskingResponse.OrderEntry(parse(offerho5()),  parse(untOfferrem5())),
                    new AskingResponse.OrderEntry(parse(offerho6()),  parse(untOfferrem6())),
                    new AskingResponse.OrderEntry(parse(offerho7()),  parse(untOfferrem7())),
                    new AskingResponse.OrderEntry(parse(offerho8()),  parse(untOfferrem8())),
                    new AskingResponse.OrderEntry(parse(offerho9()),  parse(untOfferrem9())),
                    new AskingResponse.OrderEntry(parse(offerho10()), parse(untOfferrem10()))
            );
        }

        public List<AskingResponse.OrderEntry> toBids() {
            return List.of(
                    new AskingResponse.OrderEntry(parse(bidho1()),  parse(untBidrem1())),
                    new AskingResponse.OrderEntry(parse(bidho2()),  parse(untBidrem2())),
                    new AskingResponse.OrderEntry(parse(bidho3()),  parse(untBidrem3())),
                    new AskingResponse.OrderEntry(parse(bidho4()),  parse(untBidrem4())),
                    new AskingResponse.OrderEntry(parse(bidho5()),  parse(untBidrem5())),
                    new AskingResponse.OrderEntry(parse(bidho6()),  parse(untBidrem6())),
                    new AskingResponse.OrderEntry(parse(bidho7()),  parse(untBidrem7())),
                    new AskingResponse.OrderEntry(parse(bidho8()),  parse(untBidrem8())),
                    new AskingResponse.OrderEntry(parse(bidho9()),  parse(untBidrem9())),
                    new AskingResponse.OrderEntry(parse(bidho10()), parse(untBidrem10()))
            );
        }

        private long parse(String value) {
            if (value == null || value.isBlank()) return 0L;
            try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return 0L; }
        }
    }
}
