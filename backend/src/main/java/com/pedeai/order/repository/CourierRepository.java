package com.pedeai.order.repository;

import com.pedeai.order.domain.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    List<Courier> findAllByStoreIdOrderByNameAsc(UUID storeId);

    Optional<Courier> findByIdAndStoreId(UUID id, UUID storeId);
}
