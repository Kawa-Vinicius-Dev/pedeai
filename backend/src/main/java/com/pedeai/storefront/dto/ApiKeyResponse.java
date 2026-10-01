package com.pedeai.storefront.dto;

import com.pedeai.storefront.domain.ApiKey;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** {@code keyPrefix}: o começo da chave, para reconhecer qual é. A chave inteira não volta depois da criação. */
public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        Instant createdAt,
        @Schema(types = {"string", "null"}) Instant lastUsedAt,
        @Schema(types = {"string", "null"}) Instant revokedAt
) {
    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.getCreatedAt(),
                key.getLastUsedAt(), key.getRevokedAt());
    }
}
