package com.pedeai.catalog.repository;

import com.pedeai.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByStoreIdOrderBySortOrderAscNameAsc(UUID storeId);

    List<Product> findAllByStoreIdAndCategoryIdOrderBySortOrderAscNameAsc(UUID storeId, UUID categoryId);

    Optional<Product> findByStoreIdAndCode(UUID storeId, String code);

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreIdAndCode(UUID storeId, String code);

    boolean existsByStoreIdAndCodeAndIdNot(UUID storeId, String code, UUID id);

    /**
     * Desconta do estoque direto no banco, sem passar pela versão do produto: dois pedidos ao mesmo tempo com o
     * mesmo item não podem derrubar um ao outro. Não desce de zero. Só mexe em produto que controla estoque.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p
            set p.stockQuantity = case when p.stockQuantity > :quantity then p.stockQuantity - :quantity else 0 end
            where p.id = :id and p.storeId = :storeId and p.stockQuantity is not null
            """)
    int consumeStock(@Param("storeId") UUID storeId, @Param("id") UUID id, @Param("quantity") int quantity);

    /** Pausa o produto cujo estoque acabou. Devolve 1 só para quem pausou de fato. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.available = false
            where p.id = :id and p.storeId = :storeId and p.stockQuantity = 0 and p.available = true
            """)
    int pauseIfOutOfStock(@Param("storeId") UUID storeId, @Param("id") UUID id);

    @Query("select coalesce(max(p.sortOrder), -1) from Product p where p.storeId = :storeId")
    int findMaxSortOrder(@Param("storeId") UUID storeId);
}
