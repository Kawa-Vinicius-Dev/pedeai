package com.pedeai.order.repository;

import com.pedeai.order.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {

    List<OrderStatusHistory> findAllByStoreIdAndOrderIdOrderByCreatedAtAscIdAsc(UUID storeId, UUID orderId);
}
