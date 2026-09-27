package com.pedeai.integration.dto;

/** Motivo de cancelamento aceito pela plataforma para aquele pedido, naquele momento. */
public record CancellationReasonResponse(String code, String description) {
}
