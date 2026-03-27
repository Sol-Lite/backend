package com.sollite.balance.domain.repository;

import com.sollite.balance.domain.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    Optional<PortfolioSnapshot> findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndSnapshotDate(
            Long accountId, Long simulationRoundId, LocalDate snapshotDate);

    @Query("""
            SELECT ps FROM PortfolioSnapshot ps
            WHERE ps.account.accountId = :accountId
              AND ps.simulationRound.simulationRoundId = :roundId
              AND ps.snapshotDate >= :fromDate
            ORDER BY ps.snapshotDate ASC
            """)
    List<PortfolioSnapshot> findRange(@Param("accountId") Long accountId,
                                      @Param("roundId") Long roundId,
                                      @Param("fromDate") LocalDate fromDate);

    @Query("""
            SELECT ps FROM PortfolioSnapshot ps
            WHERE ps.account.accountId = :accountId
              AND ps.simulationRound.simulationRoundId = :roundId
              AND ps.snapshotDate < :snapshotDate
            ORDER BY ps.snapshotDate DESC
            """)
    List<PortfolioSnapshot> findPreviousSnapshots(@Param("accountId") Long accountId,
                                                  @Param("roundId") Long roundId,
                                                  @Param("snapshotDate") LocalDate snapshotDate);
}
