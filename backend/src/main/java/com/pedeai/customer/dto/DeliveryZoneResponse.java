package com.pedeai.customer.dto;

import com.pedeai.customer.domain.DeliveryZone;

import java.util.UUID;

public record DeliveryZoneResponse(UUID id, String neighborhood, long feeCents, boolean active) {
    public static DeliveryZoneResponse from(DeliveryZone zone) {
        return new DeliveryZoneResponse(zone.getId(), zone.getNeighborhood(), zone.getFeeCents(), zone.isActive());
    }
}
