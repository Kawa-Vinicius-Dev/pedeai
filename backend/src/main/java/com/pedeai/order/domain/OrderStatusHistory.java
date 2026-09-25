package com.pedeai.order.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Uma linha da linha do tempo do pedido: quem mudou, de que status para qual, quando e por quê. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
    @Id
    private UUID id;
    private UUID storeId;
    private UUID orderId;
    @Enumerated(EnumType.STRING)
    private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private OrderStatus toStatus;
    @Enumerated(EnumType.STRING)
    private ActorType actorType;
    private UUID actorId;
    private String actorName;
    private String reason;
    private Instant createdAt;

    protected OrderStatusHistory() {
    }

    public OrderStatusHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, Actor actor, String reason,
                              Instant now) {
        this.id = UuidV7.generate();
        this.storeId = order.getStoreId();
        this.orderId = order.getId();
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actor.type();
        this.actorId = actor.id();
        this.actorName = actor.name();
        this.reason = reason;
        this.createdAt = now;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorName() {
        return actorName;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
