package com.pedeai.printing.dto;

import java.time.Instant;

/** Código para digitar no agente na primeira execução. Aparece uma vez só: o banco guarda o hash. */
public record PairingCodeResponse(String code, Instant expiresAt) {
}
