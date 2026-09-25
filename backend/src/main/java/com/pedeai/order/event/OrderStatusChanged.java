package com.pedeai.order.event;

import com.pedeai.order.domain.OrderStatus;

import java.util.UUID;

public record OrderStatusChanged(UUID storeId, UUID orderId, int number, OrderStatus from, OrderStatus to,
                                 long version) {
}
