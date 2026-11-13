package com.pedeai.order.dto;

import com.pedeai.order.domain.Courier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CourierResponse(UUID id, String name, @Schema(types = {"string", "null"}) String phone,
                              boolean active) {
    public static CourierResponse from(Courier courier) {
        return new CourierResponse(courier.getId(), courier.getName(), courier.getPhone(), courier.isActive());
    }
}
