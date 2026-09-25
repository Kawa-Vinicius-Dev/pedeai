package com.pedeai.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SectorRequest(
        @NotBlank(message = "Informe o nome do setor.")
        @Size(max = 60, message = "O nome pode ter até 60 caracteres.")
        String name,

        @NotNull(message = "Informe se é o setor padrão.")
        Boolean defaultSector,

        @NotNull(message = "Informe se o setor está ativo.")
        Boolean active
) {
}
