package com.pedeai.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Programa de fidelidade da loja. {@code orders} nulo desliga o programa. */
public record LoyaltyRequest(
        @Schema(types = {"integer", "null"})
        @Min(value = 2, message = "Use pelo menos 2 pedidos.")
        @Max(value = 100, message = "Use até 100 pedidos.")
        Integer orders,

        @NotNull(message = "Informe o valor do desconto.")
        @Min(value = 0, message = "O desconto não pode ser negativo.")
        @Max(value = 100_000, message = "Desconto acima do permitido.")
        Long discountCents
) {
}
