package com.pedeai.order.dto;

import com.pedeai.order.domain.ActorType;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderStatusHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record OrderStatusHistoryResponse(
        @Schema(nullable = true) OrderStatus fromStatus,
        OrderStatus toStatus,
        ActorType actorType,
        @Schema(types = {"string", "null"}) String actorName,
        @Schema(types = {"string", "null"}) String reason,
        Instant createdAt
) {
    public static OrderStatusHistoryResponse from(OrderStatusHistory entry) {
        return new OrderStatusHistoryResponse(entry.getFromStatus(), entry.getToStatus(), entry.getActorType(),
                entry.getActorName(), entry.getReason(), entry.getCreatedAt());
    }
}
