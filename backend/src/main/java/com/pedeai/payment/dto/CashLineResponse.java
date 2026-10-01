package com.pedeai.payment.dto;

import com.pedeai.payment.domain.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Uma forma de pagamento no caixa. No dinheiro, o esperado já soma o troco inicial e os suprimentos e desconta as
 * sangrias. {@code countedCents} e {@code differenceCents} só existem depois do fechamento.
 */
public record CashLineResponse(UUID paymentMethodId, String name, PaymentMethodType type, long paymentsCents,
                               long payments, long expectedCents, @Schema(types = {"integer", "null"}) Long countedCents,
                               @Schema(types = {"integer", "null"}) Long differenceCents) {
}
