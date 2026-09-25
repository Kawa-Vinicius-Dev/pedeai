package com.pedeai.realtime.dto;

import java.util.UUID;

/**
 * Aviso para as telas: o pedido mudou. A tela recarrega pelo REST; o aviso não carrega o pedido inteiro.
 * {@code type}: {@code order.created} ou {@code order.status_changed}.
 */
public record RealtimeEvent(String type, UUID orderId, int number, String status, long version) {
}
