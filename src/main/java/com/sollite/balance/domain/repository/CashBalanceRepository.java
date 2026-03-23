package com.sollite.balance.domain.repository;

import com.sollite.balance.domain.entity.CashBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CashBalanceRepository extends JpaRepository<CashBalance, Long> {

    Optional<CashBalance> findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndCurrencyCode(
            Long accountId, Long simulationRoundId, String currencyCode);

    List<CashBalance> findByAccount_AccountIdAndSimulationRound_SimulationRoundId(
            Long accountId, Long simulationRoundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cb FROM CashBalance cb WHERE cb.account.accountId = :accountId " +
           "AND cb.simulationRound.simulationRoundId = :roundId AND cb.currencyCode = :currencyCode")
    Optional<CashBalance> findForUpdate(@Param("accountId") Long accountId,
                                        @Param("roundId") Long roundId,
                                        @Param("currencyCode") String currencyCode);
}
