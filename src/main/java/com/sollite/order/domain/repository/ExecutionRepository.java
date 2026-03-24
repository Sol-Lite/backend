package com.sollite.order.domain.repository;

import com.sollite.order.domain.entity.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Optional<Execution> findByOrder_OrderId(Long orderId);

    List<Execution> findByOrder_OrderIdIn(Collection<Long> orderIds);
}
