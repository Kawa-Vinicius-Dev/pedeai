package com.pedeai.store.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** A loja (restaurante). É o tenant: todo dado de negócio pertence a uma loja. */
@Entity
@Table(name = "store")
public class Store {
    public static final String DEFAULT_TIME_ZONE = "America/Sao_Paulo";
    public static final LocalTime DEFAULT_BUSINESS_DAY_CUTOFF = LocalTime.of(5, 0);
    public static final int DEFAULT_SERVICE_FEE_BP = 1000;

    @Id
    private UUID id;
    private String name;
    private String document;
    private String phone;
    private String timezone;
    private LocalTime businessDayCutoff;
    private int serviceFeeBp;
    private boolean autoConfirmOwnOrders;
    private boolean startPreparationOnConfirm;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Store() {
    }

    public Store(String name, Instant now) {
        this.id = UuidV7.generate();
        this.name = name;
        this.timezone = DEFAULT_TIME_ZONE;
        this.businessDayCutoff = DEFAULT_BUSINESS_DAY_CUTOFF;
        this.serviceFeeBp = DEFAULT_SERVICE_FEE_BP;
        this.autoConfirmOwnOrders = true;
        this.startPreparationOnConfirm = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String document, String phone, String timezone, LocalTime businessDayCutoff,
                       int serviceFeeBp, boolean autoConfirmOwnOrders, boolean startPreparationOnConfirm,
                       Instant now) {
        this.name = name;
        this.document = document;
        this.phone = phone;
        this.timezone = timezone;
        this.businessDayCutoff = businessDayCutoff;
        this.serviceFeeBp = serviceFeeBp;
        this.autoConfirmOwnOrders = autoConfirmOwnOrders;
        this.startPreparationOnConfirm = startPreparationOnConfirm;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDocument() {
        return document;
    }

    public String getPhone() {
        return phone;
    }

    public String getTimezone() {
        return timezone;
    }

    public LocalTime getBusinessDayCutoff() {
        return businessDayCutoff;
    }

    public int getServiceFeeBp() {
        return serviceFeeBp;
    }

    public boolean isAutoConfirmOwnOrders() {
        return autoConfirmOwnOrders;
    }

    public boolean isStartPreparationOnConfirm() {
        return startPreparationOnConfirm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
