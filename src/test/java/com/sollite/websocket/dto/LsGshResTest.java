package com.sollite.websocket.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LsGshRes 단위 테스트")
class LsGshResTest {

    @Test
    @DisplayName("해외 호가 가격은 소수점 포함 double로 파싱한다")
    void toAsksAndBids_parseDecimalPrices() {
        LsGshRes.LsGshBody body = new LsGshRes.LsGshBody(
                "TSLA",
                "093000",
                "223000",
                "250.75", "251.00", "251.25", "251.50", "251.75",
                "252.00", "252.25", "252.50", "252.75", "253.00",
                "250.50", "250.25", "250.00", "249.75", "249.50",
                "249.25", "249.00", "248.75", "248.50", "248.25",
                "100", "200", "300", "400", "500",
                "600", "700", "800", "900", "1000",
                "110", "210", "310", "410", "510",
                "610", "710", "810", "910", "1010",
                "1", "2", "3", "4", "5",
                "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15",
                "16", "17", "18", "19", "20",
                "55",
                "66",
                "5500",
                "6600"
        );

        assertThat(body.toAsks().get(0).price()).isEqualTo(250.75);
        assertThat(body.toAsks().get(0).volume()).isEqualTo(100);
        assertThat(body.toBids().get(0).price()).isEqualTo(250.50);
        assertThat(body.toBids().get(0).volume()).isEqualTo(110);
    }
}
