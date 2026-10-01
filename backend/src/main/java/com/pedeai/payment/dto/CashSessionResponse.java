package com.pedeai.payment.dto;

import com.pedeai.payment.domain.CashSession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** O caixa com a conferência por forma de pagamento: ao vivo enquanto aberto, a gravada depois de fechado. */
public record CashSessionResponse(
        UUID id,
        CashSession.Status status,
        Instant openedAt,
        @Schema(types = {"string", "null"}) String openedByName,
        long openingAmountCents,
        @Schema(types = {"string", "null"}) Instant closedAt,
        @Schema(types = {"string", "null"}) String closedByName,
        @Schema(types = {"string", "null"}) String notes,
        List<CashLineResponse> lines,
        List<CashMovementResponse> movements,
        long expectedCents,
        @Schema(types = {"integer", "null"}) Long countedCents,
        @Schema(types = {"integer", "null"}) Long differenceCents
) {
}
