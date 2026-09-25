package com.pedeai.customer.dto;

import com.pedeai.customer.domain.CustomerAddress;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CustomerAddressResponse(
        UUID id,
        @Schema(types = {"string", "null"}) String label,
        String street,
        String number,
        @Schema(types = {"string", "null"}) String complement,
        String neighborhood,
        @Schema(types = {"string", "null"}) String city,
        @Schema(types = {"string", "null"}) String state,
        @Schema(types = {"string", "null"}) String postalCode,
        @Schema(types = {"string", "null"}) String reference
) {
    public static CustomerAddressResponse from(CustomerAddress address) {
        return new CustomerAddressResponse(address.getId(), address.getLabel(), address.getStreet(),
                address.getNumber(), address.getComplement(), address.getNeighborhood(), address.getCity(),
                address.getState(), address.getPostalCode(), address.getReference());
    }
}
