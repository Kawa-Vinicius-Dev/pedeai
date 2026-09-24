package com.pedeai.store.service;

import java.time.Instant;

/** Valor do refresh token recém-emitido. Só existe em memória, para ir para o cookie. */
public record IssuedRefreshToken(String value, Instant expiresAt) {
}
