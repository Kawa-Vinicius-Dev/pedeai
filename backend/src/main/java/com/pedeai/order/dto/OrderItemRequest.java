package com.pedeai.order.dto;

import com.pedeai.catalog.dto.PriceQuoteRequest.OptionChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "Informe o produto.")
        UUID productId,

        @NotNull(message = "Informe a quantidade.")
        @Min(value = 1, message = "A quantidade deve ser pelo menos 1.")
        @Max(value = 999, message = "A quantidade pode ser no máximo 999.")
        Integer quantity,

        @NotNull(message = "Informe as opções (pode ser uma lista vazia).")
        @Size(max = 50, message = "Opções demais.")
        List<@Valid @NotNull OptionChoice> options,

        @Size(max = 300, message = "A observação do item pode ter até 300 caracteres.")
        String notes
) {
}
