package com.pedeai.order.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Item do pedido: cópia de nome, código, setor e preços do cardápio na hora do pedido. */
@Entity
@Table(name = "order_item")
public class OrderItem {
    @Id
    private UUID id;
    private UUID storeId;
    private UUID productId;
    private String code;
    private String name;
    private UUID sectorId;
    private int quantity;
    private long unitPriceCents;
    private long optionsPriceCents;
    private long totalCents;
    private String notes;
    @Enumerated(EnumType.STRING)
    private ItemStatus status;
    private String cancelReason;
    private int sortOrder;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<OrderItemOption> options = new ArrayList<>();

    protected OrderItem() {
    }

    /**
     * @param unitPriceCents    preço base de uma unidade
     * @param optionsPriceCents o que as opções somam a uma unidade, já pela regra de cada grupo
     */
    public OrderItem(UUID storeId, UUID productId, String code, String name, UUID sectorId, int quantity,
                     long unitPriceCents, long optionsPriceCents, String notes, List<OrderItemOption> options,
                     int sortOrder) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.productId = productId;
        this.code = code;
        this.name = name;
        this.sectorId = sectorId;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
        this.optionsPriceCents = optionsPriceCents;
        this.totalCents = Math.multiplyExact(unitPriceCents + optionsPriceCents, quantity);
        this.notes = notes;
        this.status = ItemStatus.ACTIVE;
        this.sortOrder = sortOrder;
        this.options.addAll(options);
    }

    public boolean isActive() {
        return status == ItemStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public long getOptionsPriceCents() {
        return optionsPriceCents;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public String getNotes() {
        return notes;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public List<OrderItemOption> getOptions() {
        return Collections.unmodifiableList(options);
    }
}
