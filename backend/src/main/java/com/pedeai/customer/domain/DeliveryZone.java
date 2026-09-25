package com.pedeai.customer.domain;

import com.pedeai.shared.id.UuidV7;
import com.pedeai.shared.text.Texts;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Taxa de entrega de um bairro: o modelo mais comum em restaurante pequeno. */
@Entity
@Table(name = "delivery_zone")
public class DeliveryZone {
    @Id
    private UUID id;
    private UUID storeId;
    private String neighborhood;
    private String neighborhoodKey;
    private long feeCents;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected DeliveryZone() {
    }

    public DeliveryZone(UUID storeId, String neighborhood, long feeCents, boolean active, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.createdAt = now;
        update(neighborhood, feeCents, active, now);
    }

    public void update(String neighborhood, long feeCents, boolean active, Instant now) {
        this.neighborhood = neighborhood;
        this.neighborhoodKey = Texts.normalizeKey(neighborhood);
        this.feeCents = feeCents;
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public long getFeeCents() {
        return feeCents;
    }

    public boolean isActive() {
        return active;
    }
}
