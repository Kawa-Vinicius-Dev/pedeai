package com.pedeai.integration.repository;

import com.pedeai.integration.domain.OutboundAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

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

    @Modifying
    @Query("delete from OutboundAction a where a.createdAt < :cutoff and a.status <> :pending")
    int deleteHandledBefore(@Param("cutoff") Instant cutoff, @Param("pending") OutboundAction.Status pending);
}
