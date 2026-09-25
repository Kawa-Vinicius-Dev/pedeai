package com.pedeai.store.dto;

import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Role role, boolean active, Instant createdAt) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(),
                user.getCreatedAt());
    }
}
