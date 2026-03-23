package com.sollite.order.domain.entity;

import com.sollite.order.domain.enums.OrderEventType;
import com.sollite.order.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_event_id")
    private Long orderEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private OrderEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", length = 20)
    private OrderStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", nullable = false, length = 20)
    private OrderStatus afterStatus;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "event_message", length = 1000)
    private String eventMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public OrderEvent(Order order, OrderEventType eventType, OrderStatus beforeStatus,
                      OrderStatus afterStatus, String referenceType, String referenceId,
                      String eventMessage) {
        this.order = order;
        this.eventType = eventType;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.eventMessage = eventMessage;
    }
}
