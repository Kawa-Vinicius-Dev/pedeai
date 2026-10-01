package com.pedeai.integration.dto;

import com.pedeai.order.domain.OrderSource;

/** Uma plataforma de pedidos: {@code configured} com credenciais; só {@code simulator}, pedidos de mentira. */
public record PlatformResponse(OrderSource provider, String name, boolean configured, boolean simulator) {
}
