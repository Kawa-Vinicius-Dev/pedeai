package com.pedeai.integration.repository;

import com.pedeai.integration.domain.OutboundAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundActionRepository extends JpaRepository<OutboundAction, UUID> {

    List<OutboundAction> findAllByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboundAction.Status status, Instant now, Pageable page);

    /** A ação mais antiga ainda pendente do pedido: as ações de um pedido saem em ordem. */
    Optional<OutboundAction> findFirstByOrderIdAndStatusOrderByCreatedAtAsc(UUID orderId, OutboundAction.Status status);

    List<OutboundAction> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<OutboundAction> findByIdAndStoreId(UUID id, UUID storeId);

    long countByStoreIdAndStatus(UUID storeId, OutboundAction.Status status);
}
