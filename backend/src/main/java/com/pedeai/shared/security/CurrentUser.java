package com.pedeai.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Quem está chamando a API, lido do token de acesso. A loja ({@code storeId}) sempre vem daqui,
 * nunca da URL nem do corpo da requisição.
 */
public record CurrentUser(UUID userId, UUID storeId, Role role, String name) {
    public static final String STORE_CLAIM = "store_id";
    public static final String ROLE_CLAIM = "role";
    public static final String NAME_CLAIM = "name";

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(STORE_CLAIM)),
                Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM)),
                jwt.getClaimAsString(NAME_CLAIM)
        );
    }
}
