package com.sollite.balance.domain.repository;

import com.sollite.balance.domain.entity.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByAccount_AccountIdAndSimulationRound_SimulationRoundId(
            Long accountId, Long simulationRoundId);

    Optional<Holding> findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndInstrument_InstrumentId(
            Long accountId, Long simulationRoundId, Long instrumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Holding h WHERE h.account.accountId = :accountId " +
           "AND h.simulationRound.simulationRoundId = :roundId " +
           "AND h.instrument.instrumentId = :instrumentId")
    Optional<Holding> findForUpdate(@Param("accountId") Long accountId,
                                    @Param("roundId") Long roundId,
                                    @Param("instrumentId") Long instrumentId);
}
