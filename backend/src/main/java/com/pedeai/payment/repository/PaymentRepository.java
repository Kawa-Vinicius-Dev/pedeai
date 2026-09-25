package com.pedeai.payment.repository;

import com.pedeai.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByStoreIdAndOrderIdOrderByCreatedAtAscIdAsc(UUID storeId, UUID orderId);

    Optional<Payment> findByIdAndStoreIdAndOrderId(UUID id, UUID storeId, UUID orderId);
}
