package com.pedeai.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Abrir o caixa com o troco que está na gaveta. */
public record OpenCashSessionRequest(
        @NotNull(message = "Informe o valor de abertura (troco na gaveta).")
        @PositiveOrZero(message = "O valor de abertura não pode ser negativo.")
        @Max(value = 100_000_000, message = "Valor de abertura alto demais.")
        Long openingAmountCents
) {
}
