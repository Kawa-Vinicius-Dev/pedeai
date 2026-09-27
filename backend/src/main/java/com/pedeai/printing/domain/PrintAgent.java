package com.pedeai.printing.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Computador com o agente de impressão. O token de dispositivo só serve para imprimir e pode ser revogado. */
@Entity
@Table(name = "print_agent")
public class PrintAgent {
    /** Sem heartbeat (a cada 20 s) por mais que isso, o agente está offline. */
    public static final Duration OFFLINE_AFTER = Duration.ofSeconds(60);

    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    private String tokenHash;
    private String os;
    private String agentVersion;
    private Instant lastSeenAt;
    private Instant revokedAt;
    private Instant createdAt;
    @Version
    private Long version;

    protected PrintAgent() {
    }

    public PrintAgent(UUID storeId, String name, String tokenHash, String os, String agentVersion, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.os = os;
        this.agentVersion = agentVersion;
        this.lastSeenAt = now;
        this.createdAt = now;
    }

    public void heartbeat(String agentVersion, Instant now) {
        if (agentVersion != null) {
            this.agentVersion = agentVersion;
        }
        this.lastSeenAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isOnlineAt(Instant now) {
        return !isRevoked() && lastSeenAt != null && lastSeenAt.plus(OFFLINE_AFTER).isAfter(now);
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

    public String getOs() {
        return os;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
