package com.pedeai.storefront.repository;

import com.pedeai.storefront.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndStoreId(UUID id, UUID storeId);

    List<ApiKey> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);
}
