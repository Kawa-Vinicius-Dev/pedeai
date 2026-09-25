package com.pedeai.payment.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_method")
public class PaymentMethod {
    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    @Enumerated(EnumType.STRING)
    private PaymentMethodType type;
    private boolean active;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected PaymentMethod() {
    }

    public PaymentMethod(UUID storeId, String name, PaymentMethodType type, int sortOrder, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        update(name, type, true, now);
    }

    public void update(String name, PaymentMethodType type, boolean active, Instant now) {
        this.name = name;
        this.type = type;
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PaymentMethodType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }
}
