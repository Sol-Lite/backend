package com.sollite.account.domain.repository;

import com.sollite.account.domain.entity.SimulationRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulationRoundRepository extends JpaRepository<SimulationRound, Long> {

    Optional<SimulationRound> findByAccount_AccountIdAndRoundStatus(Long accountId, String roundStatus);
}
