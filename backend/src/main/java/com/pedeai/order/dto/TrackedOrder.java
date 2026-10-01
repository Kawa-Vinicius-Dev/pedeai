package com.pedeai.order.dto;

import java.util.UUID;

/** Pedido achado pelo código de acompanhamento, que não diz de qual loja ele é. */
public record TrackedOrder(UUID storeId, OrderResponse order) {
}
