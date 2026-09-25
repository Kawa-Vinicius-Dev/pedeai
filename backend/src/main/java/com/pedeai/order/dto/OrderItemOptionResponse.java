package com.pedeai.order.dto;

import com.pedeai.order.domain.OrderItemOption;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record OrderItemOptionResponse(
        @Schema(types = {"string", "null"}) UUID optionId,
        String groupName,
        String name,
        @Schema(types = {"string", "null"}) String code,
        int quantity,
        long unitPriceCents
) {
    public static OrderItemOptionResponse from(OrderItemOption option) {
        return new OrderItemOptionResponse(option.getOptionItemId(), option.getGroupName(), option.getName(),
                option.getCode(), option.getQuantity(), option.getUnitPriceCents());
    }
}
