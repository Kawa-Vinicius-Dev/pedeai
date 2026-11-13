package com.pedeai.order.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Entregador da loja. Não tem login: a equipe escolhe quem leva cada pedido. */
@Entity
@Table(name = "courier")
public class Courier {
    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    private String phone;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Courier() {
    }

    public Courier(UUID storeId, String name, String phone, boolean active, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.createdAt = now;
        update(name, phone, active, now);
    }

    public void update(String name, String phone, boolean active, Instant now) {
        this.name = name;
        this.phone = phone;
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }
}
