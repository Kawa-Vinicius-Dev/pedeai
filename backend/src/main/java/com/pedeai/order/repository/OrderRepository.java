package com.pedeai.order.repository;

import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.CourierSettlementResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Order> findByStoreIdAndSourceAndExternalId(UUID storeId, OrderSource source, String externalId);

    Optional<Order> findByTrackingCode(String trackingCode);

    List<Order> findAllByStoreIdAndStatusInOrderByCreatedAtAsc(UUID storeId, Collection<OrderStatus> statuses);

    /** Entregas não canceladas de um dia operacional, somadas por entregador (nulo: ficaram sem entregador). */
    @Query("""
            select new com.pedeai.order.dto.CourierSettlementResponse(
                o.courierId, max(o.courierName), count(o), coalesce(sum(o.deliveryFeeCents), 0))
            from Order o
            where o.storeId = :storeId and o.businessDate = :businessDate
              and o.type = com.pedeai.order.domain.OrderType.DELIVERY
              and o.status <> com.pedeai.order.domain.OrderStatus.CANCELLED
            group by o.courierId
            order by max(o.courierName)
            """)
    List<CourierSettlementResponse> settleCouriers(@Param("storeId") UUID storeId,
                                                   @Param("businessDate") LocalDate businessDate);
}
