package com.sollite.order.domain.repository;

import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByAccount_AccountIdAndSimulationRound_SimulationRoundIdOrderByRequestedAtDesc(
            Long accountId, Long simulationRoundId);

    List<Order> findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndOrderStatusOrderByRequestedAtDesc(
            Long accountId, Long simulationRoundId, OrderStatus orderStatus);

    List<Order> findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndOrderStatusIn(
            Long accountId, Long simulationRoundId, List<OrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
            SELECT o FROM Order o
            WHERE o.instrument.instrumentId = :instrumentId
              AND o.orderStatus = com.sollite.order.domain.enums.OrderStatus.PENDING
              AND o.orderKind = com.sollite.order.domain.enums.OrderKind.LIMIT
            ORDER BY o.requestedAt ASC
            """)
    List<Order> findPendingLimitOrders(@Param("instrumentId") Long instrumentId);

    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.instrument
            WHERE o.orderStatus = com.sollite.order.domain.enums.OrderStatus.PENDING
              AND o.orderKind = com.sollite.order.domain.enums.OrderKind.LIMIT
            """)
    List<Order> findAllPendingLimitOrders();
}
