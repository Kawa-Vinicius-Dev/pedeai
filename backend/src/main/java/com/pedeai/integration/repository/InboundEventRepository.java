package com.pedeai.integration.repository;

import com.pedeai.integration.domain.InboundEvent;
import com.pedeai.order.domain.OrderSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InboundEventRepository extends JpaRepository<InboundEvent, UUID> {

    boolean existsByProviderAndExternalEventId(OrderSource provider, String externalEventId);

    List<InboundEvent> findAllByStatusAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
            InboundEvent.Status status, Instant now, Pageable page);

    long countByExternalMerchantIdInAndStatus(Collection<String> merchantIds, InboundEvent.Status status);
}
