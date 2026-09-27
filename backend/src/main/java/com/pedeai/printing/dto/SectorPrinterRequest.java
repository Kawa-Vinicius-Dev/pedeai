package com.pedeai.printing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SectorPrinterRequest(
        @NotNull(message = "Escolha a impressora do setor.")
        UUID printerId,

        UUID backupPrinterId,

        @NotNull(message = "Informe o número de cópias.")
        @Min(value = 1, message = "Use de 1 a 5 cópias.")
        @Max(value = 5, message = "Use de 1 a 5 cópias.")
        Integer copies,

        @NotNull(message = "Informe se a impressão do setor está ligada.")
        Boolean enabled
) {
}
