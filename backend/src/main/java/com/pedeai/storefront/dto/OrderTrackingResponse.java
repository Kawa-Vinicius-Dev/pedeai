package com.pedeai.storefront.dto;

import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** O que o cliente vê do próprio pedido. Sem telefone nem endereço: quem tem o link vê só isto. */
public record OrderTrackingResponse(
        int number,
        OrderType type,
        OrderStatus status,
        String storeName,
        String storeSlug,
        @Schema(types = {"string", "null"}) String storePhone,
        List<TrackingItemResponse> items,
        long subtotalCents,
        long deliveryFeeCents,
        long totalCents,
        Instant createdAt,
        @Schema(types = {"string", "null"}) String cancelReason
) {
    public record TrackingItemResponse(int quantity, String name,
                                       @Schema(types = {"string", "null"}) String details) {
    }
}
