package com.pedeai.customer.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_address")
public class CustomerAddress {
    @Id
    private UUID id;
    private UUID storeId;
    private String label;
    private String street;
    private String number;
    private String complement;
    private String neighborhood;
    private String city;
    private String state;
    private String postalCode;
    private String reference;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected CustomerAddress() {
    }

    CustomerAddress(UUID storeId, AddressDraft draft, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.active = true;
        this.createdAt = now;
        apply(draft, now);
    }

    void apply(AddressDraft draft, Instant now) {
        this.label = draft.label();
        this.street = draft.street();
        this.number = draft.number();
        this.complement = draft.complement();
        this.neighborhood = draft.neighborhood();
        this.city = draft.city();
        this.state = draft.state();
        this.postalCode = draft.postalCode();
        this.reference = draft.reference();
        this.updatedAt = now;
    }

    /** Endereço antigo sai da lista, mas continua existindo: pedidos antigos têm a cópia dele. */
    void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    boolean samePlaceAs(AddressDraft draft) {
        return toDraft().samePlaceKey().equals(draft.samePlaceKey());
    }

    public AddressDraft toDraft() {
        return new AddressDraft(label, street, number, complement, neighborhood, city, state, postalCode, reference);
    }

    public UUID getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public String getLabel() {
        return label;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getReference() {
        return reference;
    }
}
