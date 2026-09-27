package com.pedeai.integration.dto;

import com.pedeai.integration.domain.OutboundAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Sincronização de um pedido com a plataforma: o que foi mandado, o que falhou e por quê. */
public record OutboundActionResponse(
        UUID id,
        OutboundAction.Action action,
        OutboundAction.Status status,
        int attempts,
        @Schema(types = {"string", "null"}) String lastError,
        Instant createdAt
) {
    public static OutboundActionResponse from(OutboundAction action) {
        return new OutboundActionResponse(action.getId(), action.getAction(), action.getStatus(),
                action.getAttempts(), action.getLastError(), action.getCreatedAt());
    }
}
