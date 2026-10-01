package com.pedeai.payment.dto;

import com.pedeai.payment.domain.CashSession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Linha do histórico de caixas. {@code differenceCents}: sobra (+) ou falta (-) no fechamento. */
public record CashSessionSummaryResponse(UUID id, CashSession.Status status, Instant openedAt,
                                         @Schema(types = {"string", "null"}) Instant closedAt, long openingAmountCents,
                                         @Schema(types = {"integer", "null"}) Long expectedCents,
                                         @Schema(types = {"integer", "null"}) Long countedCents,
                                         @Schema(types = {"integer", "null"}) Long differenceCents) {
    public static CashSessionSummaryResponse from(CashSession session) {
        if (session.getStatus() == CashSession.Status.OPEN) {
            return new CashSessionSummaryResponse(session.getId(), session.getStatus(), session.getOpenedAt(), null,
                    session.getOpeningAmountCents(), null, null, null);
        }
        long expected = session.getCounts().stream().mapToLong(CashSession.Count::expectedCents).sum();
        long counted = session.getCounts().stream().mapToLong(CashSession.Count::countedCents).sum();
        return new CashSessionSummaryResponse(session.getId(), session.getStatus(), session.getOpenedAt(),
                session.getClosedAt(), session.getOpeningAmountCents(), expected, counted, counted - expected);
    }
}
