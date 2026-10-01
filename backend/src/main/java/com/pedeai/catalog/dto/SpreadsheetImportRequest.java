package com.pedeai.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Planilha em CSV (separado por ponto e vírgula, vírgula ou tabulação), com cabeçalho. {@code dryRun}: só
 * pré-visualiza, sem gravar.
 */
public record SpreadsheetImportRequest(
        @NotBlank(message = "Envie a planilha.")
        @Size(max = 2_000_000, message = "A planilha pode ter até 2 MB.")
        String content,

        @NotNull(message = "Informe se é só a pré-visualização.")
        Boolean dryRun
) {
}
