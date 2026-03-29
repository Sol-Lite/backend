package com.sollite.account.domain.repository;

import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SimulationRoundRepository extends JpaRepository<SimulationRound, Long> {

    Optional<SimulationRound> findByAccount_AccountIdAndRoundStatus(Long accountId, RoundStatus roundStatus);

    @Query("""
            select sr
            from SimulationRound sr
            join fetch sr.account a
            where sr.roundStatus = :roundStatus
              and a.accountStatus = com.sollite.account.domain.enums.AccountStatus.ACTIVE
            """)
    List<SimulationRound> findAllActiveWithAccountByRoundStatus(RoundStatus roundStatus);

    @Transactional
    void deleteByAccount_AccountId(Long accountId);
}
