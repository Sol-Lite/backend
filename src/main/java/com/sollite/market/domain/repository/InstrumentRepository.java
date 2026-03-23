package com.sollite.market.domain.repository;

import com.sollite.market.domain.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    @Query("SELECT i FROM Instrument i WHERE i.activeYn = 'Y' AND " +
            "(UPPER(i.stockCode) LIKE UPPER(CONCAT(:keyword, '%')) " +
            "OR i.stockName LIKE CONCAT('%', :keyword, '%') " +
            "OR UPPER(i.stockNameEn) LIKE UPPER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY CASE WHEN UPPER(i.stockCode) = UPPER(:keyword) THEN 0 " +
            "WHEN UPPER(i.stockCode) LIKE UPPER(CONCAT(:keyword, '%')) THEN 1 ELSE 2 END")
    List<Instrument> searchByKeyword(@Param("keyword") String keyword);

    @Query(value = """
            SELECT exchange_code
            FROM instruments
            WHERE active_yn = 'Y'
              AND UPPER(stock_code) = UPPER(:stockCode)
              AND exchange_code IS NOT NULL
              AND TRIM(exchange_code) <> ''
            ORDER BY instrument_id ASC
            """, nativeQuery = true)
    List<String> findExchangeCodesByStockCode(@Param("stockCode") String stockCode);

    default Optional<String> findFirstExchangeCodeByStockCode(String stockCode) {
        return findExchangeCodesByStockCode(stockCode).stream().findFirst();
    }

    @Query("""
            SELECT i FROM Instrument i
            WHERE i.activeYn = 'Y'
              AND UPPER(i.stockCode) = UPPER(:stockCode)
              AND i.marketType = :marketType
            """)
    Optional<Instrument> findByStockCodeAndMarketType(
            @Param("stockCode") String stockCode,
            @Param("marketType") String marketType);

    @Query("""
            SELECT i FROM Instrument i
            WHERE i.activeYn = 'Y'
              AND UPPER(i.stockCode) = UPPER(:stockCode)
              AND i.marketType IN :marketTypes
            """)
    Optional<Instrument> findByStockCodeAndMarketTypeIn(
            @Param("stockCode") String stockCode,
            @Param("marketTypes") List<String> marketTypes);

    @Query("""
            SELECT i FROM Instrument i
            WHERE i.activeYn = 'Y'
              AND UPPER(i.stockCode) = UPPER(:stockCode)
              AND i.exchangeCode IS NOT NULL
            ORDER BY i.instrumentId ASC
            """)
    List<Instrument> findActiveByStockCode(@Param("stockCode") String stockCode);
}
