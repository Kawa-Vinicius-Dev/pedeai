package com.pedeai.customer.repository;

import com.pedeai.customer.domain.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID> {

    List<DeliveryZone> findAllByStoreIdOrderByNeighborhoodAsc(UUID storeId);

    Optional<DeliveryZone> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<DeliveryZone> findByStoreIdAndNeighborhoodKey(UUID storeId, String neighborhoodKey);
}
