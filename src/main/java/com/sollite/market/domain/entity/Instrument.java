package com.sollite.market.domain.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(name = "instruments")
@Getter
@NoArgsConstructor
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "market_type", nullable = false, length = 20)
    private String marketType;

    @Column(name = "instrument_type", nullable = false, length = 20)
    private String instrumentType;

    @Column(name = "exchange_code", length = 20)
    private String exchangeCode;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "standard_code", length = 20)
    private String standardCode;

    @Column(name = "stock_name", nullable = false, length = 200)
    private String stockName;

    @Column(name = "stock_name_en", length = 200)
    private String stockNameEn;

    @Column(name = "sector", length = 100)
    private String sector;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "etf_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String etfYn;

    @Column(name = "nxt_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String nxtYn;

    @Column(name = "active_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String activeYn;

    @Column(name = "market_cap")
    private Long marketCap;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Instrument(String marketType, String instrumentType, String exchangeCode,
                      String stockCode, String standardCode, String stockName,
                      String stockNameEn, String currencyCode,
                      String etfYn, String nxtYn) {
        this.marketType     = marketType;
        this.instrumentType = instrumentType;
        this.exchangeCode   = exchangeCode;
        this.stockCode      = stockCode;
        this.standardCode   = standardCode;
        this.stockName      = stockName;
        this.stockNameEn    = stockNameEn;
        this.currencyCode   = currencyCode;
        this.etfYn          = etfYn;
        this.nxtYn          = nxtYn;
        this.activeYn       = "Y";
        this.createdAt      = LocalDateTime.now();
        this.updatedAt      = LocalDateTime.now();
    }
}
