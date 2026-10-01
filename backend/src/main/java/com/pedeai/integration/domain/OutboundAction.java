package com.pedeai.integration.domain;

import com.pedeai.order.domain.OrderSource;
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

/** Ação a mandar para a plataforma (confirmar, preparar, pronto...). Gravada junto com a mudança de status. */
@Entity
@Table(name = "outbound_action")
public class OutboundAction {
    public static final int MAX_ATTEMPTS = 10;

    public enum Action {
        CONFIRM, START_PREPARATION, READY, DISPATCH, REQUEST_CANCELLATION
    }

    public enum Status {
        PENDING, DONE, FAILED, SKIPPED
    }

    @Id
    private UUID id;
    private UUID storeId;
    @Enumerated(EnumType.STRING)
    private OrderSource provider;
    private UUID orderId;
    private String externalOrderId;
    @Enumerated(EnumType.STRING)
    private Action action;
    private String payload;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant doneAt;
    @Version
    private Long version;

    protected OutboundAction() {
    }

    public OutboundAction(UUID storeId, OrderSource provider, UUID orderId, String externalOrderId, Action action,
                          String payload, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.provider = provider;
        this.orderId = orderId;
        this.externalOrderId = externalOrderId;
        this.action = action;
        this.payload = payload;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void done(Instant now) {
        this.status = Status.DONE;
        this.doneAt = now;
        this.lastError = null;
    }

    /** A plataforma recusou (4xx de regra): repetir não adianta. */
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

    /** Rede ou 5xx: tenta de novo com espera crescente (até ~30 min no total), depois falha. */
    public void retryLater(String error, Instant now) {
        this.attempts++;
        this.lastError = truncate(error);
        if (attempts >= MAX_ATTEMPTS) {
            this.status = Status.FAILED;
            return;
        }
        this.nextAttemptAt = now.plus(Duration.ofSeconds(Math.min(600, 5L << (attempts - 1))));
    }

    /** Uma pessoa mandou tentar de novo. */
    public void retryNow(Instant now) {
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
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

    public OrderSource getProvider() {
        return provider;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public Action getAction() {
        return action;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
