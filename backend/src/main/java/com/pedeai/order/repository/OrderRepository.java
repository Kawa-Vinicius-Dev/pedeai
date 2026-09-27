package com.pedeai.order.repository;

import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Order> findByStoreIdAndSourceAndExternalId(UUID storeId, OrderSource source, String externalId);

    List<Order> findAllByStoreIdAndStatusInOrderByCreatedAtAsc(UUID storeId, Collection<OrderStatus> statuses);
}
