package com.pedeai.customer.repository;

import com.pedeai.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Customer> findByStoreIdAndPhone(UUID storeId, String phone);

    boolean existsByStoreIdAndPhoneAndIdNot(UUID storeId, String phone, UUID id);

    @Query("""
            select c from Customer c
            where c.storeId = :storeId
              and (:namePattern is null or lower(c.name) like :namePattern or c.phone like :phonePattern)
            order by c.name asc
            """)
    Page<Customer> search(@Param("storeId") UUID storeId, @Param("namePattern") String namePattern,
                          @Param("phonePattern") String phonePattern, Pageable pageable);
}
