package com.sollite.balance.domain.repository;

import com.sollite.balance.domain.entity.PositionLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionLedgerRepository extends JpaRepository<PositionLedger, Long> {
}
