package com.pedeai.customer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeliveryZoneRequest(
        @NotBlank(message = "Informe o bairro.")
        @Size(max = 80, message = "O bairro pode ter até 80 caracteres.")
        String neighborhood,

        @NotNull(message = "Informe a taxa de entrega.")
        @Min(value = 0, message = "A taxa não pode ser negativa.")
        @Max(value = 100_000, message = "Taxa acima do permitido.")
        Long feeCents,

        @NotNull(message = "Informe se a taxa está ativa.")
        Boolean active
) {
}
