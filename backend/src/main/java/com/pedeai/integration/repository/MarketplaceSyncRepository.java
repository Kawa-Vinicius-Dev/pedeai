package com.pedeai.integration.repository;

import com.pedeai.integration.domain.MarketplaceSync;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MarketplaceSyncRepository extends JpaRepository<MarketplaceSync, UUID> {

    boolean existsByConnectionIdAndKindAndReferenceIdAndStatus(UUID connectionId, MarketplaceSync.Kind kind,
                                                               UUID referenceId, MarketplaceSync.Status status);

    boolean existsByConnectionIdAndKindAndReferenceIdIsNullAndStatus(UUID connectionId, MarketplaceSync.Kind kind,
                                                                     MarketplaceSync.Status status);

    List<MarketplaceSync> findAllByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            MarketplaceSync.Status status, Instant now, Pageable pageable);

    long countByConnectionIdAndStatus(UUID connectionId, MarketplaceSync.Status status);
}
