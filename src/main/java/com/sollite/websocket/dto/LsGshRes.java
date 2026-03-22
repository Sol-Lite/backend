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
            String offerno1, String offerno2, String offerno3, String offerno4, String offerno5,
            String offerno6, String offerno7, String offerno8, String offerno9, String offerno10,
            String bidno1, String bidno2, String bidno3, String bidno4, String bidno5,
            String bidno6, String bidno7, String bidno8, String bidno9, String bidno10,
            String totoffercnt,
            String totbidcnt,
            String totofferrem,
            String totbidrem
    ) {
        public List<QuoteEntry> toAsks() {
            return List.of(
                    new QuoteEntry(parseDouble(offerho1()), parseLong(offerrem1()), parseLong(offerno1())),
                    new QuoteEntry(parseDouble(offerho2()), parseLong(offerrem2()), parseLong(offerno2())),
                    new QuoteEntry(parseDouble(offerho3()), parseLong(offerrem3()), parseLong(offerno3())),
                    new QuoteEntry(parseDouble(offerho4()), parseLong(offerrem4()), parseLong(offerno4())),
                    new QuoteEntry(parseDouble(offerho5()), parseLong(offerrem5()), parseLong(offerno5())),
                    new QuoteEntry(parseDouble(offerho6()), parseLong(offerrem6()), parseLong(offerno6())),
                    new QuoteEntry(parseDouble(offerho7()), parseLong(offerrem7()), parseLong(offerno7())),
                    new QuoteEntry(parseDouble(offerho8()), parseLong(offerrem8()), parseLong(offerno8())),
                    new QuoteEntry(parseDouble(offerho9()), parseLong(offerrem9()), parseLong(offerno9())),
                    new QuoteEntry(parseDouble(offerho10()), parseLong(offerrem10()), parseLong(offerno10()))
            );
        }

        public List<QuoteEntry> toBids() {
            return List.of(
                    new QuoteEntry(parseDouble(bidho1()), parseLong(bidrem1()), parseLong(bidno1())),
                    new QuoteEntry(parseDouble(bidho2()), parseLong(bidrem2()), parseLong(bidno2())),
                    new QuoteEntry(parseDouble(bidho3()), parseLong(bidrem3()), parseLong(bidno3())),
                    new QuoteEntry(parseDouble(bidho4()), parseLong(bidrem4()), parseLong(bidno4())),
                    new QuoteEntry(parseDouble(bidho5()), parseLong(bidrem5()), parseLong(bidno5())),
                    new QuoteEntry(parseDouble(bidho6()), parseLong(bidrem6()), parseLong(bidno6())),
                    new QuoteEntry(parseDouble(bidho7()), parseLong(bidrem7()), parseLong(bidno7())),
                    new QuoteEntry(parseDouble(bidho8()), parseLong(bidrem8()), parseLong(bidno8())),
                    new QuoteEntry(parseDouble(bidho9()), parseLong(bidrem9()), parseLong(bidno9())),
                    new QuoteEntry(parseDouble(bidho10()), parseLong(bidrem10()), parseLong(bidno10()))
            );
        }

        private long parseLong(String value) {
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
