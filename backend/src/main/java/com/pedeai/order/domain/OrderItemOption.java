package com.pedeai.order.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Opção escolhida no item, com o preço dela no cardápio na hora do pedido. */
@Entity
@Table(name = "order_item_option")
public class OrderItemOption {
    @Id
    private UUID id;
    private UUID storeId;
    private UUID optionItemId;
    private String groupName;
    private String name;
    private String code;
    private int quantity;
    private long unitPriceCents;
    private int sortOrder;

    protected OrderItemOption() {
    }

    public OrderItemOption(UUID storeId, UUID optionItemId, String groupName, String name, String code, int quantity,
                           long unitPriceCents, int sortOrder) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.optionItemId = optionItemId;
        this.groupName = groupName;
        this.name = name;
        this.code = code;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
        this.sortOrder = sortOrder;
    }

    public UUID getOptionItemId() {
        return optionItemId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }
}
