package com.pedeai.store.domain;

import com.pedeai.shared.id.UuidV7;
import com.pedeai.shared.security.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Pessoa da equipe de uma loja. {@code user} é palavra reservada no SQL, por isso {@code app_user}. */
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;
    private UUID storeId;
    private String name;
    private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected AppUser() {
    }

    public AppUser(UUID storeId, String name, String email, String passwordHash, Role role, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void changeRole(Role role, Instant now) {
        this.role = role;
        this.updatedAt = now;
    }

    public void changeActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
