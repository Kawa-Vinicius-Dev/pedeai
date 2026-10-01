package com.pedeai.payment.repository;

import com.pedeai.payment.domain.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findAllByCashSessionIdOrderByCreatedAtAscIdAsc(UUID cashSessionId);
}
