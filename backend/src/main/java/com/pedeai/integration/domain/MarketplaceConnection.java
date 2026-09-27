package com.pedeai.integration.domain;

import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** A loja ligada a um merchant do iFood. Um merchant só pode estar ligado a uma loja. */
@Entity
@Table(name = "marketplace_connection")
public class MarketplaceConnection {
    public enum Status {
        ACTIVE, PAUSED, ERROR
    }

    @Id
    private UUID id;
    private UUID storeId;
    @Enumerated(EnumType.STRING)
    private OrderSource provider;
    private String externalMerchantId;
    private String merchantName;
    @Enumerated(EnumType.STRING)
    private Status status;
    private boolean autoConfirm;
    private Instant lastEventAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected MarketplaceConnection() {
    }

    public MarketplaceConnection(UUID storeId, OrderSource provider, String externalMerchantId, String merchantName,
                                 boolean autoConfirm, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.provider = provider;
        this.externalMerchantId = externalMerchantId;
        this.merchantName = merchantName;
        this.status = Status.ACTIVE;
        this.autoConfirm = autoConfirm;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Status status, boolean autoConfirm, Instant now) {
        this.status = status;
        this.autoConfirm = autoConfirm;
        this.updatedAt = now;
    }

    public void eventReceived(Instant now) {
        this.lastEventAt = now;
    }

    public void failed(String error, Instant now) {
        this.lastError = error == null || error.length() <= 300 ? error : error.substring(0, 300);
        this.updatedAt = now;
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

    public String getExternalMerchantId() {
        return externalMerchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isAutoConfirm() {
        return autoConfirm;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public String getLastError() {
        return lastError;
    }
}
