package com.pedeai.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Acerto de um entregador num dia: quantas entregas fez e quanto somam as taxas de entrega delas. Entregas
 * canceladas não contam. {@code courierId} nulo: entregas do dia que ficaram sem entregador.
 */
public record CourierSettlementResponse(
        @Schema(types = {"string", "null"}) UUID courierId,
        @Schema(types = {"string", "null"}) String courierName,
        long deliveries,
        long deliveryFeeCents
) {
}
