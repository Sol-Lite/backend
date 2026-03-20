package com.sollite.websocket.dto;

public record LsGscRes(
        LsGscHeader header,
        LsGscBody body
) {
    public record LsGscHeader(
            String tr_cd
    ) {}

    public record LsGscBody(
            String symbol,
            String ovsdate,
            String kordate,
            String trdtm,
            String kortm,
            String sign,
            String price,
            String diff,
            String rate,
            String open,
            String high,
            String low,
            String trdq,
            String totq,
            String cgubun,
            String lSeq,
            String amount,
            String high52p,
            String low52p
    ) {
        public double parsePrice() { return parseDouble(price()); }
        public double parseDiff() { return parseDouble(diff()); }
        public double parseRate() { return parseDouble(rate()); }
        public double parseOpen() { return parseDouble(open()); }
        public double parseHigh() { return parseDouble(high()); }
        public double parseLow() { return parseDouble(low()); }
        public long parseTrdq() { return parse(trdq()); }
        public long parseTotq() { return parse(totq()); }
        public long parseAmount() { return parse(amount()); }
        public double parseHigh52p() { return parseDouble(high52p()); }
        public double parseLow52p() { return parseDouble(low52p()); }

        private long parse(String value) {
            if (value == null || value.isBlank()) return 0L;
            try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return 0L; }
        }

        private double parseDouble(String value) {
            if (value == null || value.isBlank()) return 0.0;
            try { return Double.parseDouble(value.trim()); } catch (NumberFormatException e) { return 0.0; }
        }
    }
}
