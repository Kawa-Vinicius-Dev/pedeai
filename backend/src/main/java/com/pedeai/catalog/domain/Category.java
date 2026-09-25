package com.pedeai.catalog.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category")
public class Category {
    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    /** Setor dos produtos da categoria que não definem o próprio. */
    private UUID defaultSectorId;
    private int sortOrder;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Category() {
    }

    public Category(UUID storeId, String name, UUID defaultSectorId, int sortOrder, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.name = name;
        this.defaultSectorId = defaultSectorId;
        this.sortOrder = sortOrder;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, UUID defaultSectorId, int sortOrder, boolean active, Instant now) {
        this.name = name;
        this.defaultSectorId = defaultSectorId;
        this.sortOrder = sortOrder;
        this.active = active;
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

    public UUID getDefaultSectorId() {
        return defaultSectorId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
