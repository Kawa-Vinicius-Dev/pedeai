package com.pedeai.order.service;

import com.pedeai.order.domain.Courier;
import com.pedeai.order.domain.Order;
import com.pedeai.order.dto.CourierRequest;
import com.pedeai.order.dto.CourierResponse;
import com.pedeai.order.dto.CourierSettlementResponse;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.repository.CourierRepository;
import com.pedeai.order.repository.OrderRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.text.Texts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Entregadores da loja: cadastro, quem leva cada pedido e o acerto do dia. */
@Service
public class CourierService {
    static final String NOT_FOUND = "Entregador não encontrado.";
    static final String ORDER_NOT_FOUND = "Pedido não encontrado.";
    static final String INACTIVE = "Este entregador está inativo.";

    private final CourierRepository couriers;
    private final OrderRepository orders;
    private final Clock clock;

    public CourierService(CourierRepository couriers, OrderRepository orders, Clock clock) {
        this.couriers = couriers;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CourierResponse> list(UUID storeId) {
        return couriers.findAllByStoreIdOrderByNameAsc(storeId).stream().map(CourierResponse::from).toList();
    }

    @Transactional
    public CourierResponse create(UUID storeId, CourierRequest request) {
        Courier courier = new Courier(storeId, request.name().trim(), Texts.trimToNull(request.phone()),
                request.active(), Instant.now(clock));
        return CourierResponse.from(couriers.save(courier));
    }

    @Transactional
    public CourierResponse update(UUID storeId, UUID id, CourierRequest request) {
        Courier courier = find(storeId, id);
        courier.update(request.name().trim(), Texts.trimToNull(request.phone()), request.active(),
                Instant.now(clock));
        return CourierResponse.from(courier);
    }

    /** Define (ou tira, com {@code courierId} nulo) quem leva o pedido. */
    @Transactional
    public OrderResponse assign(UUID storeId, UUID orderId, UUID courierId) {
        Order order = orders.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND));
        Courier courier = courierId == null ? null : find(storeId, courierId);
        if (courier != null && !courier.isActive()) {
            throw new BusinessRuleException(INACTIVE);
        }
        order.assignCourier(courier);
        return OrderResponse.from(order);
    }

    /** O acerto de um dia operacional, por entregador. */
    @Transactional(readOnly = true)
    public List<CourierSettlementResponse> settlement(UUID storeId, LocalDate businessDate) {
        return orders.settleCouriers(storeId, businessDate);
    }

    private Courier find(UUID storeId, UUID id) {
        return couriers.findByIdAndStoreId(id, storeId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
