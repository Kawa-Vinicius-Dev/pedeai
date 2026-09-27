package com.pedeai.printing.dto;

import com.pedeai.printing.domain.DocumentType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Reimprimir um documento do pedido numa impressora. {@code sectorId} é obrigatório no ticket de produção. */
public record ReprintRequest(
        @NotNull(message = "Escolha o documento.")
        DocumentType documentType,

        UUID sectorId,

        @NotNull(message = "Escolha a impressora.")
        UUID printerId
) {
}
