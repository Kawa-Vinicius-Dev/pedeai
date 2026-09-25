package com.pedeai.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ProductRequest(
        @NotNull(message = "Escolha a categoria.")
        UUID categoryId,

        @Size(max = 40, message = "O código PDV pode ter até 40 caracteres.")
        String code,

        @NotBlank(message = "Informe o nome do produto.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String name,

        @Size(max = 500, message = "A descrição pode ter até 500 caracteres.")
        String description,

        @NotNull(message = "Informe o preço.")
        @Min(value = 0, message = "O preço não pode ser negativo.")
        @Max(value = 10_000_000, message = "Preço acima do permitido.")
        Long priceCents,

        /* Nulo usa o setor da categoria e, depois, o padrão da loja. */
        UUID sectorId,

        @NotNull(message = "Informe os grupos de adicionais (pode ser uma lista vazia).")
        @Size(max = 20, message = "O produto pode ter até 20 grupos de adicionais.")
        List<@NotNull UUID> optionGroupIds,

        @NotNull(message = "Informe se o produto está disponível.")
        Boolean available,

        @NotNull(message = "Informe se o produto está ativo.")
        Boolean active
) {
}
