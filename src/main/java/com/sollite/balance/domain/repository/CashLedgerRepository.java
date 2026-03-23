package com.sollite.balance.domain.repository;

import com.sollite.balance.domain.entity.CashLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashLedgerRepository extends JpaRepository<CashLedger, Long> {
}
