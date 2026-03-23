package com.sollite.watchlist.domain.repository;

import com.sollite.market.domain.entity.Instrument;
import com.sollite.watchlist.domain.entity.WatchlistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    @EntityGraph(attributePaths = {"instrument"})
    List<WatchlistItem> findByUserIdOrderByDisplayOrderAsc(Long userId);

    boolean existsByUserIdAndInstrument(Long userId, Instrument instrument);

    @Query("SELECT COALESCE(MAX(wi.displayOrder), 0) FROM WatchlistItem wi WHERE wi.userId = :userId")
    int findMaxDisplayOrderByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM WatchlistItem wi WHERE wi.userId = :userId AND wi.instrument.stockCode = :stockCode")
    int deleteByUserIdAndStockCode(@Param("userId") Long userId, @Param("stockCode") String stockCode);

    @Modifying
    @Query("UPDATE WatchlistItem wi SET wi.displayOrder = :displayOrder WHERE wi.userId = :userId AND wi.instrument.stockCode = :stockCode")
    void updateDisplayOrder(@Param("userId") Long userId, @Param("stockCode") String stockCode, @Param("displayOrder") int displayOrder);
}
