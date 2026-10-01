package com.pedeai.payment.repository;

import java.util.UUID;

/** Soma dos pagamentos de uma forma de pagamento num período. */
public record MethodTotal(UUID paymentMethodId, long totalCents, long payments) {
}
