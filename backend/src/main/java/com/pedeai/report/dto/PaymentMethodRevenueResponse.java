package com.pedeai.report.dto;

import java.util.UUID;

/** Pagamentos de uma forma de pagamento. {@code pendingCents}: parte ainda não recebida (na entrega, por exemplo). */
public record PaymentMethodRevenueResponse(UUID paymentMethodId, String name, String type, long payments,
                                           long totalCents, long pendingCents) {
}
