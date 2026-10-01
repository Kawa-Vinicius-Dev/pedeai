package com.pedeai.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedir o cancelamento à plataforma, com um motivo da lista dela. */
public record MarketplaceCancellationRequest(
        @NotBlank(message = "Escolha o motivo.")
        @Size(max = 20, message = "Código de motivo inválido.")
        String code,

        @NotBlank(message = "Escolha o motivo.")
        @Size(max = 200, message = "A descrição pode ter até 200 caracteres.")
        String description
) {
}
