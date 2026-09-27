package com.pedeai.order.event;

import com.pedeai.order.domain.ActorType;
import com.pedeai.order.domain.OrderStatus;

import java.util.UUID;

/** {@code actor}: quem mudou. Mudança que veio do marketplace não volta para ele. */
public record OrderStatusChanged(UUID storeId, UUID orderId, int number, OrderStatus from, OrderStatus to,
                                 long version, ActorType actor) {
}
