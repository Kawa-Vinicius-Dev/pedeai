package com.pedeai.storefront.dto;

import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** O pedido para o sistema de terceiros. {@code trackingCode}: o cliente acompanha em /loja/{slug}/pedido/{código}. */
public record PartnerOrderResponse(
        UUID id,
        int number,
        OrderStatus status,
        @Schema(types = {"string", "null"}) String externalId,
        String trackingCode,
        long subtotalCents,
        long deliveryFeeCents,
        long totalCents,
        Instant createdAt,
        @Schema(types = {"string", "null"}) String cancelReason
) {
    public static PartnerOrderResponse from(OrderResponse order) {
        return new PartnerOrderResponse(order.id(), order.number(), order.status(), order.externalId(),
                order.trackingCode(), order.subtotalCents(), order.deliveryFeeCents(), order.totalCents(),
                order.createdAt(), order.cancelReason());
    }
}
