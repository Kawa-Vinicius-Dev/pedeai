package com.pedeai.storefront.dto;

import com.pedeai.payment.domain.PaymentMethodType;

import java.util.UUID;

/** Formas de pagar na entrega ou na retirada. Só dinheiro aceita troco. */
public record MenuPaymentMethodResponse(UUID id, String name, PaymentMethodType type) {
}
