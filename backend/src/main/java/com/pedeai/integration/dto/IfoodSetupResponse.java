package com.pedeai.integration.dto;

/**
 * {@code configured}: o servidor tem as credenciais do iFood. {@code simulator}: dá para injetar pedidos simulados.
 */
public record IfoodSetupResponse(boolean configured, boolean simulator) {
}
