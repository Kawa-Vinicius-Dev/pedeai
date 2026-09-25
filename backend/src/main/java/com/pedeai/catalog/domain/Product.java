package com.pedeai.catalog.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {
    @Id
    private UUID id;
    private UUID storeId;
    private UUID categoryId;
    /** Código PDV: o mesmo cadastrado no cardápio do iFood e da 99Food. */
    private String code;
    private String name;
    private String description;
    private long priceCents;
    /** Setor próprio do produto. Nulo usa o da categoria e, depois, o padrão da loja. */
    private UUID sectorId;
    /** Pausado quando acaba algo durante o serviço. Volta sem precisar editar o produto. */
    private boolean available;
    private boolean active;
    private int sortOrder;
    @ElementCollection
    @CollectionTable(name = "product_option_group", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "option_group_id")
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 100)
    private List<UUID> optionGroupIds = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Product() {
    }

    public Product(UUID storeId, ProductDraft draft, int sortOrder, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        update(draft, now);
    }

    public void update(ProductDraft draft, Instant now) {
        this.categoryId = draft.categoryId();
        this.code = draft.code();
        this.name = draft.name();
        this.description = draft.description();
        this.priceCents = draft.priceCents();
        this.sectorId = draft.sectorId();
        this.available = draft.available();
        this.active = draft.active();
        this.optionGroupIds.clear();
        this.optionGroupIds.addAll(draft.optionGroupIds());
        this.updatedAt = now;
    }

    public void changeAvailability(boolean available, Instant now) {
        this.available = available;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public UUID getSectorId() {
        return sectorId;
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

    public List<UUID> getOptionGroupIds() {
        return Collections.unmodifiableList(optionGroupIds);
    }
}
