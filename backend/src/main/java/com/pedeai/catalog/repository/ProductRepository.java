package com.pedeai.catalog.repository;

import com.pedeai.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByStoreIdOrderBySortOrderAscNameAsc(UUID storeId);

    List<Product> findAllByStoreIdAndCategoryIdOrderBySortOrderAscNameAsc(UUID storeId, UUID categoryId);

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreIdAndCode(UUID storeId, String code);

    boolean existsByStoreIdAndCodeAndIdNot(UUID storeId, String code, UUID id);

    @Query("select coalesce(max(p.sortOrder), -1) from Product p where p.storeId = :storeId")
    int findMaxSortOrder(@Param("storeId") UUID storeId);
}
