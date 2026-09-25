package com.pedeai.store.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Sessão de um dispositivo. O banco guarda só o hash do token; o valor fica no cookie do navegador. */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    private UUID id;
    private UUID storeId;
    private UUID userId;
    private String tokenHash;
    private String deviceName;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;
    @Enumerated(EnumType.STRING)
    private RevokeReason revokeReason;
    @Version
    private Long version;

    protected RefreshToken() {
    }

    public RefreshToken(UUID storeId, UUID userId, String tokenHash, String deviceName, Instant now,
                        Instant expiresAt) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.deviceName = deviceName;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** Foi trocado por outro token há pouco tempo: duas abas renovando juntas, não roubo. */
    public boolean wasRotatedWithin(Duration window, Instant now) {
        return revokeReason == RevokeReason.ROTATED && !revokedAt.plus(window).isBefore(now);
    }

    public void revoke(Instant now, RevokeReason reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RevokeReason getRevokeReason() {
        return revokeReason;
    }
}
