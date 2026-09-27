package com.pedeai.integration.dto;

import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.order.domain.OrderSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** O vínculo e a saúde dele: último evento, último erro e ações com falha. */
public record ConnectionResponse(
        UUID id,
        OrderSource provider,
        String externalMerchantId,
        @Schema(types = {"string", "null"}) String merchantName,
        MarketplaceConnection.Status status,
        boolean autoConfirm,
        @Schema(types = {"string", "null"}) Instant lastEventAt,
        @Schema(types = {"string", "null"}) String lastError,
        long failedActions
) {
    public static ConnectionResponse from(MarketplaceConnection connection, long failedActions) {
        return new ConnectionResponse(connection.getId(), connection.getProvider(),
                connection.getExternalMerchantId(), connection.getMerchantName(), connection.getStatus(),
                connection.isAutoConfirm(), connection.getLastEventAt(), connection.getLastError(), failedActions);
    }
}
