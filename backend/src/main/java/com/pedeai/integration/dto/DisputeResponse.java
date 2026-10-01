package com.pedeai.integration.dto;

import com.pedeai.integration.domain.MarketplaceDispute;
import com.pedeai.order.domain.OrderSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de cancelamento feito pelo cliente no app. {@code expiresAt}: até quando a loja pode responder (nulo: o app
 * não informou). {@code rejectReasons}: os motivos que o app aceita para recusar.
 */
public record DisputeResponse(
        UUID id,
        UUID orderId,
        int orderNumber,
        OrderSource provider,
        String kind,
        @Schema(types = {"string", "null"}) String message,
        @Schema(types = {"string", "null"}) Instant expiresAt,
        MarketplaceDispute.Status status,
        java.util.List<CancellationReasonResponse> rejectReasons
) {
}
