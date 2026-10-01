package com.pedeai.integration.domain;

import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * O cliente pediu, pelo app, para cancelar o pedido (no iFood, "disputa"). A loja aceita ou recusa até o prazo; sem
 * resposta, quem decide é o app.
 */
@Entity
@Table(name = "marketplace_dispute")
public class MarketplaceDispute {
    public enum Status { OPEN, ACCEPTED, REJECTED, CLOSED }

    static final String ALREADY_ANSWERED = "Este pedido de cancelamento já foi respondido.";
    static final String EXPIRED = "O prazo para responder acabou: quem decide agora é o app.";

    @Id
    private UUID id;
    private UUID storeId;
    private UUID orderId;
    @Enumerated(EnumType.STRING)
    private OrderSource provider;
    private String externalDisputeId;
    private String kind;
    private String message;
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    private Status status;
    private UUID decidedBy;
    private Instant decidedAt;
    private Instant createdAt;
    @Version
    private Long version;

    protected MarketplaceDispute() {
    }

    public MarketplaceDispute(UUID storeId, UUID orderId, OrderSource provider, String externalDisputeId, String kind,
                              String message, Instant expiresAt, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.orderId = orderId;
        this.provider = provider;
        this.externalDisputeId = externalDisputeId;
        this.kind = kind;
        this.message = message == null || message.length() <= 500 ? message : message.substring(0, 500);
        this.expiresAt = expiresAt;
        this.status = Status.OPEN;
        this.createdAt = now;
    }

    /** A loja respondeu: a resposta segue para o app pela fila de saída. */
    public void decide(boolean accept, UUID userId, Instant now) {
        if (status != Status.OPEN) {
            throw new BusinessRuleException(ALREADY_ANSWERED);
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            throw new BusinessRuleException(EXPIRED);
        }
        this.status = accept ? Status.ACCEPTED : Status.REJECTED;
        this.decidedBy = userId;
        this.decidedAt = now;
    }

    /** O app encerrou a disputa (respondida aqui, decidida por ele, ou o pedido terminou). */
    public void close(Instant now) {
        if (status == Status.OPEN) {
            this.status = Status.CLOSED;
            this.decidedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderSource getProvider() {
        return provider;
    }

    public String getExternalDisputeId() {
        return externalDisputeId;
    }

    public String getKind() {
        return kind;
    }

    public String getMessage() {
        return message;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
