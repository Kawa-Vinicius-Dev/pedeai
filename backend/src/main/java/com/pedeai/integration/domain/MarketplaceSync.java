package com.pedeai.integration.domain;

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

/**
 * Algo da loja para mandar ao app: abrir ou pausar ({@code STORE_STATUS}), o horário ({@code OPENING_HOURS}) ou um
 * produto do cardápio ({@code ITEM}). Não guarda o conteúdo: lê o estado atual na hora do envio.
 */
@Entity
@Table(name = "marketplace_sync")
public class MarketplaceSync {
    public enum Kind { STORE_STATUS, OPENING_HOURS, ITEM }

    public enum Status { PENDING, DONE, FAILED, SKIPPED }

    public static final int MAX_ATTEMPTS = 10;

    @Id
    private UUID id;
    private UUID storeId;
    private UUID connectionId;
    @Enumerated(EnumType.STRING)
    private Kind kind;
    private UUID referenceId;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant doneAt;
    @Version
    private Long version;

    protected MarketplaceSync() {
    }

    public MarketplaceSync(UUID storeId, UUID connectionId, Kind kind, UUID referenceId, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.connectionId = connectionId;
        this.kind = kind;
        this.referenceId = referenceId;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void done(Instant now) {
        this.status = Status.DONE;
        this.doneAt = now;
        this.lastError = null;
    }

    /** O app recusou (4xx de regra) ou o item não dá para mandar: repetir não adianta. */
    public void rejected(String error, Instant now) {
        this.status = Status.FAILED;
        this.lastError = truncate(error);
        this.doneAt = now;
    }

    public void skipped(String reason, Instant now) {
        this.status = Status.SKIPPED;
        this.lastError = truncate(reason);
        this.doneAt = now;
    }

    /** Rede ou 5xx: tenta de novo com espera crescente, depois falha. */
    public void retryLater(String error, Instant now) {
        this.attempts++;
        this.lastError = truncate(error);
        if (attempts >= MAX_ATTEMPTS) {
            this.status = Status.FAILED;
            return;
        }
        this.nextAttemptAt = now.plus(Duration.ofSeconds(Math.min(600, 5L << (attempts - 1))));
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 300 ? text : text.substring(0, 300);
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public Status getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }
}
