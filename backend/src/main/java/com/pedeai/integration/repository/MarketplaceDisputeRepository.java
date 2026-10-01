package com.pedeai.integration.repository;

import com.pedeai.integration.domain.MarketplaceDispute;
import com.pedeai.order.domain.OrderSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceDisputeRepository extends JpaRepository<MarketplaceDispute, UUID> {

    Optional<MarketplaceDispute> findByProviderAndExternalDisputeId(OrderSource provider, String externalDisputeId);

    Optional<MarketplaceDispute> findByIdAndStoreId(UUID id, UUID storeId);

    List<MarketplaceDispute> findAllByStoreIdAndStatusOrderByCreatedAtAsc(UUID storeId, MarketplaceDispute.Status status);

    List<MarketplaceDispute> findAllByOrderIdAndStatus(UUID orderId, MarketplaceDispute.Status status);
}
