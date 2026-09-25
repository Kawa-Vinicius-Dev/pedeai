package com.pedeai.order.event;

import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.OrderPaymentRequest;

import java.util.List;
import java.util.UUID;

/**
 * Pedido criado. Ouvintes na mesma transação gravam o que depende dele (os pagamentos informados no
 * lançamento); ouvintes depois do commit avisam as telas.
 */
public record OrderCreated(UUID storeId, UUID orderId, int number, OrderStatus status, long version, long totalCents,
                           List<OrderPaymentRequest> payments, UUID createdBy) {
}
