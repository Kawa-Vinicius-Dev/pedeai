package com.pedeai.order.dto;

import com.pedeai.order.domain.DeliveryAddress;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeliveryAddressResponse(
        String street,
        String number,
        @Schema(types = {"string", "null"}) String complement,
        String neighborhood,
        @Schema(types = {"string", "null"}) String city,
        @Schema(types = {"string", "null"}) String state,
        @Schema(types = {"string", "null"}) String postalCode,
        @Schema(types = {"string", "null"}) String reference
) {
    public static DeliveryAddressResponse from(DeliveryAddress address) {
        return address == null ? null : new DeliveryAddressResponse(address.getStreet(), address.getNumber(),
                address.getComplement(), address.getNeighborhood(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getReference());
    }
}
