package com.pedeai.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Pagamento informado ao lançar o pedido. {@code paid = false} é "paga na entrega/retirada".
 * {@code changeForCents} é o "troco para", só em dinheiro.
 */
public record OrderPaymentRequest(
        @NotNull(message = "Escolha a forma de pagamento.")
        UUID paymentMethodId,

        @NotNull(message = "Informe o valor do pagamento.")
        @Min(value = 1, message = "O valor do pagamento deve ser maior que zero.")
        Long amountCents,

        @Min(value = 1, message = "O troco deve ser para um valor maior que zero.")
        Long changeForCents,

        @NotNull(message = "Informe se já foi pago.")
        Boolean paid
) {
}
