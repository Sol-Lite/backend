package com.sollite.order.domain.repository;

import com.sollite.order.domain.entity.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrder_OrderIdOrderByCreatedAtAsc(Long orderId);
}
