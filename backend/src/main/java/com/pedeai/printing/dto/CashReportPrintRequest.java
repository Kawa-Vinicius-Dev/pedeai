package com.pedeai.printing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Imprimir o relatório do caixa numa impressora. */
public record CashReportPrintRequest(
        @NotNull(message = "Escolha a impressora.")
        UUID printerId
) {
}
