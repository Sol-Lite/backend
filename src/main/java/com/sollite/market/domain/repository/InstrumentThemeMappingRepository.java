package com.sollite.market.domain.repository;

import com.sollite.market.domain.entity.InstrumentThemeMapping;
import com.sollite.market.domain.enums.StockTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import com.sollite.market.domain.entity.Instrument;

public interface InstrumentThemeMappingRepository extends JpaRepository<InstrumentThemeMapping, Long> {

    @Query("SELECT m.instrument.stockCode FROM InstrumentThemeMapping m WHERE m.themeCode = :theme")
    Set<String> findStockCodesByTheme(@Param("theme") StockTheme theme);

    @Query("SELECT m.instrument FROM InstrumentThemeMapping m JOIN m.instrument WHERE m.themeCode = :theme")
    List<Instrument> findInstrumentsByTheme(@Param("theme") StockTheme theme);

    @Query("SELECT m.instrument FROM InstrumentThemeMapping m JOIN m.instrument WHERE m.themeCode = :theme ORDER BY m.instrument.marketCap DESC NULLS LAST")
    List<Instrument> findTopInstrumentByThemeOrderByMarketCapDesc(@Param("theme") StockTheme theme, org.springframework.data.domain.Pageable pageable);
}
