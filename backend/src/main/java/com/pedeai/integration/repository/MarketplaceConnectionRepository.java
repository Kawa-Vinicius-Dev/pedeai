package com.pedeai.integration.repository;

import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.order.domain.OrderSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceConnectionRepository extends JpaRepository<MarketplaceConnection, UUID> {

    List<MarketplaceConnection> findAllByStoreIdOrderByCreatedAtAsc(UUID storeId);

    Optional<MarketplaceConnection> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<MarketplaceConnection> findByProviderAndExternalMerchantId(OrderSource provider, String merchantId);

    List<MarketplaceConnection> findAllByProviderAndStatus(OrderSource provider, MarketplaceConnection.Status status);
}
