package com.pedeai.payment.repository;

import com.pedeai.payment.domain.CashSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    Optional<CashSession> findByStoreIdAndStatus(UUID storeId, CashSession.Status status);

    Optional<CashSession> findByIdAndStoreId(UUID id, UUID storeId);

    Page<CashSession> findAllByStoreId(UUID storeId, Pageable pageable);
}
