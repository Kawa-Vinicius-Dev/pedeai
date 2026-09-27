package com.pedeai.printing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Faixa de alerta nas telas da loja: "Impressora Cozinha offline: 3 pedidos aguardando". */
public record PrintAlertResponse(
        @Schema(types = {"string", "null"}) UUID printerId,
        String message,
        int waitingJobs
) {
}
