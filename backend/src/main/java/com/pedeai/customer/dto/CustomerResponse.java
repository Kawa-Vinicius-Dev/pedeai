package com.pedeai.customer.dto;

import com.pedeai.customer.domain.Customer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Cliente com os endereços ativos. */
public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        @Schema(types = {"string", "null"}) String email,
        @Schema(types = {"string", "null"}) String notes,
        List<CustomerAddressResponse> addresses
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail(),
                customer.getNotes(), customer.getActiveAddresses().stream().map(CustomerAddressResponse::from).toList());
    }
}
