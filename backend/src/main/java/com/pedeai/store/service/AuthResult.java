package com.pedeai.store.service;

import com.pedeai.store.dto.AuthResponse;

/** Corpo da resposta de autenticação mais o refresh token que o controller coloca no cookie. */
public record AuthResult(AuthResponse response, IssuedRefreshToken refreshToken) {
}
