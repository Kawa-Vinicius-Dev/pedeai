package com.pedeai.payment.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Sangria (tira dinheiro da gaveta) ou suprimento (coloca). */
@Entity
@Table(name = "cash_movement")
public class CashMovement {
    public enum Type { WITHDRAWAL, DEPOSIT }

    @Id
    private UUID id;
    private UUID storeId;
    private UUID cashSessionId;
    @Enumerated(EnumType.STRING)
    private Type type;
    private long amountCents;
    private String reason;
    private UUID createdBy;
    private Instant createdAt;

    protected CashMovement() {
    }

    public CashMovement(CashSession session, Type type, long amountCents, String reason, UUID userId, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = session.getStoreId();
        this.cashSessionId = session.getId();
        this.type = type;
        this.amountCents = amountCents;
        this.reason = reason;
        this.createdBy = userId;
        this.createdAt = now;
    }

    /** Quanto muda o dinheiro na gaveta: sangria negativo, suprimento positivo. */
    public long signedCents() {
        return type == Type.WITHDRAWAL ? -amountCents : amountCents;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
