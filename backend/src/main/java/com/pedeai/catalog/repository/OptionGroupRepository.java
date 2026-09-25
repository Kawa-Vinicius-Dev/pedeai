package com.pedeai.catalog.repository;

import com.pedeai.catalog.domain.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, UUID> {

    List<OptionGroup> findAllByStoreIdOrderByNameAsc(UUID storeId);

    Optional<OptionGroup> findByIdAndStoreId(UUID id, UUID storeId);

    List<OptionGroup> findAllByStoreIdAndIdIn(UUID storeId, Collection<UUID> ids);
}
