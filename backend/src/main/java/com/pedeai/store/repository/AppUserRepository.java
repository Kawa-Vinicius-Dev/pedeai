package com.pedeai.store.repository;

import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByIdAndStoreId(UUID id, UUID storeId);

    List<AppUser> findAllByStoreIdOrderByNameAsc(UUID storeId);

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStoreIdAndRoleAndActiveTrue(UUID storeId, Role role);
}
