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

/** Evento recebido da plataforma. Gravado antes de processar: nada depende de a rede funcionar naquele instante. */
@Entity
@Table(name = "inbound_event")
public class InboundEvent {
    public static final int MAX_ATTEMPTS = 10;

    public enum Status {
        PENDING, PROCESSED, IGNORED, FAILED
    }

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private OrderSource provider;
    private String externalEventId;
    private String externalMerchantId;
    private String externalOrderId;
    private String eventCode;
    private String payload;
    private UUID storeId;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant receivedAt;
    private Instant processedAt;
    @Version
    private Long version;

    protected InboundEvent() {
    }

    public InboundEvent(OrderSource provider, String externalEventId, String externalMerchantId,
                        String externalOrderId, String eventCode, String payload, Instant now) {
        this.id = UuidV7.generate();
        this.provider = provider;
        this.externalEventId = externalEventId;
        this.externalMerchantId = externalMerchantId;
        this.externalOrderId = externalOrderId;
        this.eventCode = eventCode;
        this.payload = payload;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.receivedAt = now;
    }

    public void processed(UUID storeId, Instant now) {
        this.storeId = storeId;
        this.status = Status.PROCESSED;
        this.processedAt = now;
        this.lastError = null;
    }

    public void ignored(UUID storeId, String reason, Instant now) {
        this.storeId = storeId;
        this.status = Status.IGNORED;
        this.lastError = truncate(reason);
        this.processedAt = now;
    }

    /** Erro ao processar: tenta de novo com espera crescente (até ~30 min), depois falha de vez. */
    public void retryLater(String error, Instant now) {
        this.attempts++;
        this.lastError = truncate(error);
        if (attempts >= MAX_ATTEMPTS) {
            this.status = Status.FAILED;
            return;
        }
        this.nextAttemptAt = now.plus(Duration.ofSeconds(Math.min(1800, 5L << (attempts - 1))));
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 300 ? text : text.substring(0, 300);
    }

    public UUID getId() {
        return id;
    }

    public OrderSource getProvider() {
        return provider;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getExternalMerchantId() {
        return externalMerchantId;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public String getEventCode() {
        return eventCode;
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

    public String getLastError() {
        return lastError;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
