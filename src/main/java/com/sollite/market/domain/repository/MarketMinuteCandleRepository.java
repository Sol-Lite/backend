package com.sollite.market.domain.repository;

import com.sollite.market.domain.entity.MarketMinuteCandle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketMinuteCandleRepository extends JpaRepository<MarketMinuteCandle, Long> {

    Optional<MarketMinuteCandle> findByInstrument_InstrumentIdAndExchangeScopeAndIntervalMinuteAndCandleAt(
            Long instrumentId, String exchangeScope, Integer intervalMinute, LocalDateTime candleAt);

    boolean existsByInstrument_InstrumentIdAndExchangeScopeAndIntervalMinuteAndCandleAt(
            Long instrumentId, String exchangeScope, Integer intervalMinute, LocalDateTime candleAt);

    @Query("""
            SELECT MAX(c.candleAt)
            FROM MarketMinuteCandle c
            WHERE c.instrument.instrumentId = :instrumentId
              AND c.exchangeScope = :exchangeScope
              AND c.intervalMinute = :intervalMinute
            """)
    LocalDateTime findLatestCandleAt(
            @Param("instrumentId") Long instrumentId,
            @Param("exchangeScope") String exchangeScope,
            @Param("intervalMinute") Integer intervalMinute);

    @Query("""
            SELECT c FROM MarketMinuteCandle c
            WHERE c.instrument.instrumentId = :instrumentId
              AND c.exchangeScope = :exchangeScope
              AND c.intervalMinute = :intervalMinute
              AND c.candleAt BETWEEN :startAt AND :endAt
            ORDER BY c.candleAt ASC
            """)
    List<MarketMinuteCandle> findByRange(
            @Param("instrumentId") Long instrumentId,
            @Param("exchangeScope") String exchangeScope,
            @Param("intervalMinute") Integer intervalMinute,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    @Query("""
            SELECT c FROM MarketMinuteCandle c
            WHERE c.instrument.instrumentId = :instrumentId
              AND c.exchangeScope = :exchangeScope
              AND c.intervalMinute = :intervalMinute
            ORDER BY c.candleAt DESC
            """)
    List<MarketMinuteCandle> findLatestN(
            @Param("instrumentId") Long instrumentId,
            @Param("exchangeScope") String exchangeScope,
            @Param("intervalMinute") Integer intervalMinute,
            Pageable pageable);

    @Query("""
            SELECT c FROM MarketMinuteCandle c
            WHERE c.instrument.instrumentId = :instrumentId
              AND c.exchangeScope = :exchangeScope
              AND c.intervalMinute = :intervalMinute
              AND c.candleAt < :before
            ORDER BY c.candleAt DESC
            """)
    List<MarketMinuteCandle> findBefore(
            @Param("instrumentId") Long instrumentId,
            @Param("exchangeScope") String exchangeScope,
            @Param("intervalMinute") Integer intervalMinute,
            @Param("before") LocalDateTime before,
            Pageable pageable);
}
