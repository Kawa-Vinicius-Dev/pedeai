package com.pedeai.payment.repository;

import com.pedeai.payment.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findAllByStoreIdOrderBySortOrderAscNameAsc(UUID storeId);

    List<PaymentMethod> findAllByStoreIdAndIdIn(UUID storeId, Collection<UUID> ids);

    Optional<PaymentMethod> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreId(UUID storeId);

    boolean existsByStoreIdAndNameIgnoreCase(UUID storeId, String name);

    boolean existsByStoreIdAndNameIgnoreCaseAndIdNot(UUID storeId, String name, UUID id);

    @Query("select coalesce(max(m.sortOrder), -1) from PaymentMethod m where m.storeId = :storeId")
    int findMaxSortOrder(@Param("storeId") UUID storeId);
}
