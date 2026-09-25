package com.pedeai.catalog.repository;

import com.pedeai.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByStoreIdOrderBySortOrderAscNameAsc(UUID storeId);

    Optional<Category> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreIdAndNameIgnoreCase(UUID storeId, String name);

    boolean existsByStoreIdAndNameIgnoreCaseAndIdNot(UUID storeId, String name, UUID id);

    @Query("select coalesce(max(c.sortOrder), -1) from Category c where c.storeId = :storeId")
    int findMaxSortOrder(@Param("storeId") UUID storeId);
}
