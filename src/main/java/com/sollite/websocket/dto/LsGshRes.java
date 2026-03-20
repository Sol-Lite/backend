package com.sollite.websocket.dto;

import java.util.List;

public record LsGshRes(
        LsGshHeader header,
        LsGshBody body
) {
    public record LsGshHeader(
            String tr_cd
    ) {}

    public record LsGshBody(
            String symbol,
            String loctime,
            String kortime,
            String offerho1, String offerho2, String offerho3, String offerho4, String offerho5,
            String offerho6, String offerho7, String offerho8, String offerho9, String offerho10,
            String bidho1, String bidho2, String bidho3, String bidho4, String bidho5,
            String bidho6, String bidho7, String bidho8, String bidho9, String bidho10,
            String offerrem1, String offerrem2, String offerrem3, String offerrem4, String offerrem5,
            String offerrem6, String offerrem7, String offerrem8, String offerrem9, String offerrem10,
            String bidrem1, String bidrem2, String bidrem3, String bidrem4, String bidrem5,
            String bidrem6, String bidrem7, String bidrem8, String bidrem9, String bidrem10,
            String totoffercnt,
            String totbidcnt,
            String totofferrem,
            String totbidrem
    ) {
        public List<QuoteEntry> toAsks() {
            return List.of(
                    new QuoteEntry(parse(offerho1()), parse(offerrem1()), parse(offerno1())),
                    new QuoteEntry(parse(offerho2()), parse(offerrem2()), parse(offerno2())),
                    new QuoteEntry(parse(offerho3()), parse(offerrem3()), parse(offerno3())),
                    new QuoteEntry(parse(offerho4()), parse(offerrem4()), parse(offerno4())),
                    new QuoteEntry(parse(offerho5()), parse(offerrem5()), parse(offerno5())),
                    new QuoteEntry(parse(offerho6()), parse(offerrem6()), parse(offerno6())),
                    new QuoteEntry(parse(offerho7()), parse(offerrem7()), parse(offerno7())),
                    new QuoteEntry(parse(offerho8()), parse(offerrem8()), parse(offerno8())),
                    new QuoteEntry(parse(offerho9()), parse(offerrem9()), parse(offerno9())),
                    new QuoteEntry(parse(offerho10()), parse(offerrem10()), parse(offerno10()))
            );
        }

        public List<QuoteEntry> toBids() {
            return List.of(
                    new QuoteEntry(parse(bidho1()), parse(bidrem1()), parse(bidno1())),
                    new QuoteEntry(parse(bidho2()), parse(bidrem2()), parse(bidno2())),
                    new QuoteEntry(parse(bidho3()), parse(bidrem3()), parse(bidno3())),
                    new QuoteEntry(parse(bidho4()), parse(bidrem4()), parse(bidno4())),
                    new QuoteEntry(parse(bidho5()), parse(bidrem5()), parse(bidno5())),
                    new QuoteEntry(parse(bidho6()), parse(bidrem6()), parse(bidno6())),
                    new QuoteEntry(parse(bidho7()), parse(bidrem7()), parse(bidno7())),
                    new QuoteEntry(parse(bidho8()), parse(bidrem8()), parse(bidno8())),
                    new QuoteEntry(parse(bidho9()), parse(bidrem9()), parse(bidno9())),
                    new QuoteEntry(parse(bidho10()), parse(bidrem10()), parse(bidno10()))
            );
        }

        private long parse(String value) {
            if (value == null || value.isBlank()) return 0L;
            try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return 0L; }
        }

        private double parseDouble(String value) {
            if (value == null || value.isBlank()) return 0.0;
            try { return Double.parseDouble(value.trim()); } catch (NumberFormatException e) { return 0.0; }
        }
    }

    public record QuoteEntry(
            double price,
            long volume,
            long orderCount
    ) {}
}
