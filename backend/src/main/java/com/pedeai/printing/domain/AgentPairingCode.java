package com.pedeai.printing.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Código de 6 dígitos que o agente troca pelo token. Vale uma vez só e por pouco tempo. */
@Entity
@Table(name = "agent_pairing_code")
public class AgentPairingCode {
    @Id
    private UUID id;
    private UUID storeId;
    private String codeHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;
    @Version
    private Long version;

    protected AgentPairingCode() {
    }

    public AgentPairingCode(UUID storeId, String codeHash, Instant expiresAt, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void use(Instant now) {
        this.usedAt = now;
    }

    public UUID getStoreId() {
        return storeId;
    }
}
