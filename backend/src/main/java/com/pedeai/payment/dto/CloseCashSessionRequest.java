package com.pedeai.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Fechamento: o que foi contado em cada forma de pagamento. Forma não informada conta como zero. */
public record CloseCashSessionRequest(
        @NotNull(message = "Informe a contagem.")
        @Size(max = 50, message = "Formas de pagamento demais.")
        List<@Valid @NotNull Count> counts,

        @Size(max = 300, message = "A observação pode ter até 300 caracteres.")
        String notes
) {
    public record Count(
            @NotNull(message = "Informe a forma de pagamento.")
            UUID paymentMethodId,

            @NotNull(message = "Informe o valor contado.")
            @PositiveOrZero(message = "O valor contado não pode ser negativo.")
            @Max(value = 1_000_000_000, message = "Valor contado alto demais.")
            Long countedCents
    ) {
    }
}
