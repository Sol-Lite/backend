package com.sollite.market.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_minute_candles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketMinuteCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "minute_candle_id")
    private Long minuteCandleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "exchange_scope", nullable = false, columnDefinition = "CHAR(1)")
    private String exchangeScope;

    @Column(name = "interval_minute", nullable = false)
    private Integer intervalMinute;

    @Column(name = "candle_at", nullable = false)
    private LocalDateTime candleAt;

    @Column(name = "open_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Column(name = "trading_value")
    private Long tradingValue;

    @Column(name = "adjustment_code")
    private Long adjustmentCode;

    @Column(name = "adjustment_rate", precision = 10, scale = 4)
    private BigDecimal adjustmentRate;

    @Column(name = "price_sign", columnDefinition = "CHAR(1)")
    private String priceSign;

    @Column(name = "source_tr_cd", nullable = false, length = 10)
    private String sourceTrCd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public MarketMinuteCandle(
            Instrument instrument,
            String exchangeScope,
            Integer intervalMinute,
            LocalDateTime candleAt,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            Long volume,
            Long tradingValue,
            Long adjustmentCode,
            BigDecimal adjustmentRate,
            String priceSign,
            String sourceTrCd
    ) {
        this.instrument = instrument;
        this.exchangeScope = exchangeScope;
        this.intervalMinute = intervalMinute;
        this.candleAt = candleAt;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.tradingValue = tradingValue;
        this.adjustmentCode = adjustmentCode;
        this.adjustmentRate = adjustmentRate;
        this.priceSign = priceSign;
        this.sourceTrCd = sourceTrCd;
    }
}
