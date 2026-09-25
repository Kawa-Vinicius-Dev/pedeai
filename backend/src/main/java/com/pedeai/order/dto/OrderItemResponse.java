package com.pedeai.order.dto;

import com.pedeai.order.domain.ItemStatus;
import com.pedeai.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        @Schema(types = {"string", "null"}) UUID productId,
        @Schema(types = {"string", "null"}) String code,
        String name,
        @Schema(types = {"string", "null"}) UUID sectorId,
        int quantity,
        long unitPriceCents,
        long optionsPriceCents,
        long totalCents,
        @Schema(types = {"string", "null"}) String notes,
        ItemStatus status,
        List<OrderItemOptionResponse> options
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getCode(), item.getName(),
                item.getSectorId(), item.getQuantity(), item.getUnitPriceCents(), item.getOptionsPriceCents(),
                item.getTotalCents(), item.getNotes(), item.getStatus(),
                item.getOptions().stream().map(OrderItemOptionResponse::from).toList());
    }
}
