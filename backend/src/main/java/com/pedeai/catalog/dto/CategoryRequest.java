package com.pedeai.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** {@code sortOrder} nulo põe a categoria no fim (ao criar) ou mantém a posição (ao editar). */
public record CategoryRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 80, message = "O nome pode ter até 80 caracteres.")
        String name,

        UUID defaultSectorId,

        @Min(value = 0, message = "A posição não pode ser negativa.")
        Integer sortOrder,

        @NotNull(message = "Informe se a categoria está ativa.")
        Boolean active
) {
}
