package com.pedeai.printing.dto;

import java.util.UUID;

/** Quem está chamando a API do agente: autenticado pelo token de dispositivo, nunca por login de pessoa. */
public record AgentPrincipal(UUID agentId, UUID storeId) {
}
