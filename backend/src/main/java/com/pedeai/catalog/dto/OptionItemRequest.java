package com.pedeai.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** {@code id} nulo cria uma opção nova no grupo. */
public record OptionItemRequest(
        UUID id,

        @Size(max = 40, message = "O código PDV pode ter até 40 caracteres.")
        String code,

        @NotBlank(message = "Informe o nome da opção.")
        @Size(max = 80, message = "O nome pode ter até 80 caracteres.")
        String name,

        @NotNull(message = "Informe o preço da opção.")
        @Min(value = 0, message = "O preço não pode ser negativo.")
        @Max(value = 10_000_000, message = "Preço acima do permitido.")
        Long priceCents,

        @NotNull(message = "Informe se a opção está disponível.")
        Boolean available,

        @NotNull(message = "Informe se a opção está ativa.")
        Boolean active
) {
}
