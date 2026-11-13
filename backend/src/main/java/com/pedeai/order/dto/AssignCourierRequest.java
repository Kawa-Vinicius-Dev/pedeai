package com.pedeai.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** {@code courierId} nulo tira o entregador do pedido. */
public record AssignCourierRequest(@Schema(types = {"string", "null"}) UUID courierId) {
}
