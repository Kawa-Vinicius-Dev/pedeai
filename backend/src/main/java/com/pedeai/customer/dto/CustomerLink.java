package com.pedeai.customer.dto;

import java.util.UUID;

/**
 * O cliente (e o endereço, na entrega) que ficaram gravados a partir de um pedido. {@code phone} já vem no
 * formato gravado (E.164).
 */
public record CustomerLink(UUID customerId, UUID addressId, String phone) {
}
