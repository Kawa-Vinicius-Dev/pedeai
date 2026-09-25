package com.pedeai.customer.domain;

import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cliente dos canais próprios da loja (balcão, telefone, WhatsApp). O telefone é a chave. */
@Entity
@Table(name = "customer")
public class Customer {
    static final String ADDRESS_NOT_FOUND = "Endereço não encontrado.";

    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    private String phone;
    private String email;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)
    private List<CustomerAddress> addresses = new ArrayList<>();

    protected Customer() {
    }

    public Customer(UUID storeId, String name, String phone, String email, String notes, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.createdAt = now;
        update(name, phone, email, notes, now);
    }

    public void update(String name, String phone, String email, String notes, Instant now) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.notes = notes;
        this.updatedAt = now;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public CustomerAddress addAddress(AddressDraft draft, Instant now) {
        CustomerAddress address = new CustomerAddress(storeId, draft, now);
        addresses.add(address);
        this.updatedAt = now;
        return address;
    }

    /** O endereço ativo que já é este lugar, se houver. Evita duplicar o endereço a cada pedido. */
    public Optional<CustomerAddress> findSamePlace(AddressDraft draft) {
        return addresses.stream().filter(CustomerAddress::isActive).filter(a -> a.samePlaceAs(draft)).findFirst();
    }

    public CustomerAddress updateAddress(UUID addressId, AddressDraft draft, Instant now) {
        CustomerAddress address = activeAddress(addressId);
        address.apply(draft, now);
        this.updatedAt = now;
        return address;
    }

    public void removeAddress(UUID addressId, Instant now) {
        activeAddress(addressId).deactivate(now);
        this.updatedAt = now;
    }

    public CustomerAddress activeAddress(UUID addressId) {
        return addresses.stream()
                .filter(address -> address.getId().equals(addressId) && address.isActive())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ADDRESS_NOT_FOUND));
    }

    public List<CustomerAddress> getActiveAddresses() {
        return addresses.stream().filter(CustomerAddress::isActive).toList();
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

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getNotes() {
        return notes;
    }
}
