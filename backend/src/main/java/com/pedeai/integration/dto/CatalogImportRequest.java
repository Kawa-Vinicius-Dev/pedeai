package com.pedeai.integration.dto;

import jakarta.validation.constraints.NotNull;

/** {@code dryRun}: só mostra o que seria importado, sem gravar. */
public record CatalogImportRequest(
        @NotNull(message = "Informe se é só a pré-visualização.")
        Boolean dryRun
) {
}
