package com.pedeai.catalog.repository;

import com.pedeai.catalog.domain.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectorRepository extends JpaRepository<Sector, UUID> {

    List<Sector> findAllByStoreIdOrderBySortOrderAscNameAsc(UUID storeId);

    Optional<Sector> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Sector> findByStoreIdAndDefaultSectorTrue(UUID storeId);

    boolean existsByStoreIdAndNameIgnoreCase(UUID storeId, String name);

    boolean existsByStoreIdAndNameIgnoreCaseAndIdNot(UUID storeId, String name, UUID id);

    @Query("select coalesce(max(s.sortOrder), -1) from Sector s where s.storeId = :storeId")
    int findMaxSortOrder(@Param("storeId") UUID storeId);
}
