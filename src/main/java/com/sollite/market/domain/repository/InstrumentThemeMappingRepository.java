package com.sollite.market.domain.repository;

import com.sollite.market.domain.entity.InstrumentThemeMapping;
import com.sollite.market.domain.enums.StockTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface InstrumentThemeMappingRepository extends JpaRepository<InstrumentThemeMapping, Long> {

    @Query("SELECT m.instrument.stockCode FROM InstrumentThemeMapping m WHERE m.themeCode = :theme")
    Set<String> findStockCodesByTheme(@Param("theme") StockTheme theme);
}
