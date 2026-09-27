package com.pedeai.printing.dto;

import java.util.UUID;

/** {@code token}: token de dispositivo. O agente guarda e manda em toda chamada como {@code Bearer}. */
public record AgentPairingResponse(UUID agentId, String agentName, String storeName, String token) {
}
