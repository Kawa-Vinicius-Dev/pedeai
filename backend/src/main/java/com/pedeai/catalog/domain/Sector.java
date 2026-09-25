package com.pedeai.catalog.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Setor de produção (Cozinha, Bar, Pizzaria): para onde vai cada item do pedido. */
@Entity
@Table(name = "sector")
public class Sector {
    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    @Column(name = "is_default")
    private boolean defaultSector;
    private boolean active;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Sector() {
    }

    public Sector(UUID storeId, String name, boolean defaultSector, int sortOrder, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.name = name;
        this.defaultSector = defaultSector;
        this.active = true;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, boolean active, Instant now) {
        this.name = name;
        this.active = active;
        this.updatedAt = now;
    }

    public void changeDefault(boolean defaultSector, Instant now) {
        this.defaultSector = defaultSector;
        this.updatedAt = now;
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

    public boolean isDefaultSector() {
        return defaultSector;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
