package com.pedeai.payment.dto;

import com.pedeai.payment.domain.CashMovement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record CashMovementResponse(UUID id, CashMovement.Type type, long amountCents, String reason,
                                   @Schema(types = {"string", "null"}) String createdByName, Instant createdAt) {
}
