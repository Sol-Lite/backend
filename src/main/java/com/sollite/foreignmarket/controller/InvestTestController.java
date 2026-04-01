package com.sollite.foreignmarket.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sollite.global.service.KisTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/invest")
public class InvestTestController {

    private final WebClient kisWebClient;
    private final KisTokenService kisTokenService;

    public InvestTestController(
            @Qualifier("kisWebClient") WebClient kisWebClient,
            KisTokenService kisTokenService) {
        this.kisWebClient = kisWebClient;
        this.kisTokenService = kisTokenService;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Output1(String rsym, String zdiv, String stim, String etim, String next, String nrec) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Output2(String tymd, String xymd, String xhms, String kymd, String khms,
                   String open, String high, String low, String last, String evol, String eamt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KisNminResponse(String rt_cd, String msg_cd, String msg1, Output1 output1, List<Output2> output2) {}

    /**
     * KIS 해외주식 분봉 조회 테스트
     * GET /invest?symb=AVGO&excd=NAS&nmin=1&nrec=30
     */
    @GetMapping
    public KisNminResponse getNminChart(
            @RequestParam(defaultValue = "AVGO") String symb,
            @RequestParam(defaultValue = "NAS") String excd,
            @RequestParam(defaultValue = "1") String nmin,
            @RequestParam(defaultValue = "30") String nrec) {

        String token = kisTokenService.getAccessToken();

        log.info("[InvestTest] KIS 분봉 조회: symb={}, excd={}, nmin={}, nrec={}", symb, excd, nmin, nrec);

        return kisWebClient.get()
                .uri(u -> u.path("/uapi/overseas-price/v1/quotations/inquire-time-itemchartprice")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", excd)
                        .queryParam("SYMB", symb)
                        .queryParam("NMIN", nmin)
                        .queryParam("PINC", "1")
                        .queryParam("NEXT", "")
                        .queryParam("NREC", nrec)
                        .queryParam("FILL", "")
                        .queryParam("KEYB", "")
                        .build())
                .header("authorization", "Bearer " + token)
                .header("tr_id", "HHDFS76950200")
                .header("custtype", "P")
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .bodyToMono(KisNminResponse.class)
                .block();
    }
}
