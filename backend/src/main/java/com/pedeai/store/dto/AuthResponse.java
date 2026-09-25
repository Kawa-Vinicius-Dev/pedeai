package com.pedeai.store.dto;

/** Token de acesso para o header {@code Authorization}. O refresh token vai só no cookie HttpOnly. */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        StoreSummaryResponse store
) {
}
