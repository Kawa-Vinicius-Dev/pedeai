package com.pedeai.storefront.repository;

import com.pedeai.storefront.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);

    Optional<Coupon> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Coupon> findByStoreIdAndCode(UUID storeId, String code);
}
