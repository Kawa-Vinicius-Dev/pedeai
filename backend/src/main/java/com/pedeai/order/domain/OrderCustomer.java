package com.pedeai.order.domain;

import java.util.UUID;

/** Cliente copiado no pedido. {@code id} é nulo quando não há cadastro (retirada só com o nome). */
public record OrderCustomer(UUID id, String name, String phone) {
}
