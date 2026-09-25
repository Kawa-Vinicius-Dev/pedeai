package com.pedeai.catalog.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Uma opção de um grupo de adicionais (um sabor, um adicional, um ponto da carne). */
@Entity
@Table(name = "option_item")
public class OptionItem {
    @Id
    private UUID id;
    private UUID storeId;
    private String code;
    private String name;
    private long priceCents;
    private boolean available;
    private boolean active;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected OptionItem() {
    }

    OptionItem(UUID storeId, OptionDraft draft, int sortOrder, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.createdAt = now;
        apply(draft, sortOrder, now);
    }

    void apply(OptionDraft draft, int sortOrder, Instant now) {
        this.code = draft.code();
        this.name = draft.name();
        this.priceCents = draft.priceCents();
        this.available = draft.available();
        this.active = draft.active();
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    /** Opção retirada do grupo: some do cardápio, mas continua existindo para o histórico. */
    void retire(int sortOrder, Instant now) {
        this.active = false;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public void changeAvailability(boolean available, Instant now) {
        this.available = available;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
