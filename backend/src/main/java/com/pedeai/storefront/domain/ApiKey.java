package com.pedeai.storefront.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Chave da API de pedidos de uma loja. Guarda só o SHA-256: a chave em si aparece uma vez, na criação. */
@Entity
@Table(name = "api_key")
public class ApiKey {
    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    private String keyPrefix;
    private String keyHash;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;

    protected ApiKey() {
    }

    public ApiKey(UUID storeId, String name, String keyPrefix, String keyHash, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.createdAt = now;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void used(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
