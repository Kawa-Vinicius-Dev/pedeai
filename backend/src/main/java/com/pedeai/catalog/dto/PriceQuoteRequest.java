package com.pedeai.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PriceQuoteRequest(
        @NotNull(message = "Informe a quantidade.")
        @Min(value = 1, message = "A quantidade deve ser pelo menos 1.")
        @Max(value = 999, message = "A quantidade pode ser no máximo 999.")
        Integer quantity,

        @NotNull(message = "Informe as opções (pode ser uma lista vazia).")
        @Size(max = 50, message = "Opções demais.")
        List<@Valid @NotNull OptionChoice> options
) {
    public record OptionChoice(
            @NotNull(message = "Informe a opção.")
            UUID optionId,

            @NotNull(message = "Informe a quantidade da opção.")
            @Min(value = 1, message = "A quantidade da opção deve ser pelo menos 1.")
            @Max(value = 99, message = "A quantidade da opção pode ser no máximo 99.")
            Integer quantity
    ) {
    }
}
