package com.pedeai.integration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Aceitar ou recusar o cancelamento. Recusar pede um dos motivos do app ({@code rejectCode}). */
public record DisputeAnswerRequest(
        @NotNull(message = "Informe se aceita o cancelamento.")
        Boolean accept,

        @Size(max = 60, message = "Motivo inválido.")
        String rejectCode
) {
}
