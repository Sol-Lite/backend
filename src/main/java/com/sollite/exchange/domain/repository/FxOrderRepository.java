package com.sollite.exchange.domain.repository;

import com.sollite.exchange.domain.entity.FxOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxOrderRepository extends JpaRepository<FxOrder, Long> {
}
