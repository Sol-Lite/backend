package com.sollite.market.domain.repository;

import com.sollite.market.domain.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    @Query("SELECT i FROM Instrument i WHERE i.activeYn = 'Y' AND " +
            "(UPPER(i.stockCode) LIKE UPPER(CONCAT(:keyword, '%')) " +
            "OR i.stockName LIKE CONCAT('%', :keyword, '%') " +
            "OR UPPER(i.stockNameEn) LIKE UPPER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY CASE WHEN UPPER(i.stockCode) = UPPER(:keyword) THEN 0 " +
            "WHEN UPPER(i.stockCode) LIKE UPPER(CONCAT(:keyword, '%')) THEN 1 ELSE 2 END")
    List<Instrument> searchByKeyword(@Param("keyword") String keyword);
}
