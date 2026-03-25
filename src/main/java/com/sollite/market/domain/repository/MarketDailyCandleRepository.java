package com.sollite.market.domain.repository;

import com.sollite.market.domain.entity.MarketDailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketDailyCandleRepository extends JpaRepository<MarketDailyCandle, Long> {

    Optional<MarketDailyCandle> findByInstrument_InstrumentIdAndExchangeScopeAndTradeDate(
            Long instrumentId, String exchangeScope, LocalDate tradeDate);

    boolean existsByInstrument_InstrumentIdAndExchangeScopeAndTradeDate(
            Long instrumentId, String exchangeScope, LocalDate tradeDate);

    @Query("""
            SELECT MAX(c.tradeDate)
            FROM MarketDailyCandle c
            WHERE c.instrument.instrumentId = :instrumentId
              AND c.exchangeScope = :exchangeScope
            """)
    LocalDate findLatestTradeDate(
            @Param("instrumentId") Long instrumentId,
            @Param("exchangeScope") String exchangeScope);

    List<MarketDailyCandle> findByInstrument_InstrumentIdAndExchangeScopeOrderByTradeDateDesc(
            Long instrumentId,
            String exchangeScope,
            Pageable pageable);

    @Query("""
            SELECT c FROM MarketDailyCandle c
            WHERE c.instrument.instrumentId = :instrumentId
              AND c.exchangeScope = :exchangeScope
              AND c.tradeDate BETWEEN :startDate AND :endDate
            ORDER BY c.tradeDate ASC
            """)
    List<MarketDailyCandle> findByRange(
            @Param("instrumentId") Long instrumentId,
            @Param("exchangeScope") String exchangeScope,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
