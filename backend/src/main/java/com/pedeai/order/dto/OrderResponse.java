package com.pedeai.order.dto;

import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderItem;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Pedido completo: itens com opções, cliente, endereço, totais e os horários de cada etapa. */
public record OrderResponse(
        UUID id,
        int number,
        LocalDate businessDate,
        OrderType type,
        OrderSource source,
        OrderStatus status,
        @Schema(types = {"string", "null"}) UUID customerId,
        @Schema(types = {"string", "null"}) String customerName,
        @Schema(types = {"string", "null"}) String customerPhone,
        @Schema(nullable = true) DeliveryAddressResponse deliveryAddress,
        @Schema(types = {"string", "null"}) String notes,
        List<OrderItemResponse> items,
        long subtotalCents,
        long discountCents,
        long deliveryFeeCents,
        long additionalFeeCents,
        long platformSubsidyCents,
        long totalCents,
        Instant createdAt,
        @Schema(types = {"string", "null"}) Instant confirmedAt,
        @Schema(types = {"string", "null"}) Instant preparationStartedAt,
        @Schema(types = {"string", "null"}) Instant readyAt,
        @Schema(types = {"string", "null"}) Instant dispatchedAt,
        @Schema(types = {"string", "null"}) Instant completedAt,
        @Schema(types = {"string", "null"}) Instant cancelledAt,
        @Schema(types = {"string", "null"}) String cancelReason,
        long version
) {
    public static OrderResponse from(Order order) {
        return from(order, item -> true);
    }

    /** Só com os itens que passam no filtro. Os totais continuam sendo os do pedido inteiro. */
    public static OrderResponse from(Order order, Predicate<OrderItem> itemFilter) {
        return new OrderResponse(order.getId(), order.getNumber(), order.getBusinessDate(), order.getType(),
                order.getSource(), order.getStatus(), order.getCustomerId(), order.getCustomerName(),
                order.getCustomerPhone(), DeliveryAddressResponse.from(order.getDeliveryAddress()), order.getNotes(),
                order.getItems().stream().filter(itemFilter).map(OrderItemResponse::from).toList(), order.getSubtotalCents(),
                order.getDiscountCents(), order.getDeliveryFeeCents(), order.getAdditionalFeeCents(),
                order.getPlatformSubsidyCents(), order.getTotalCents(), order.getCreatedAt(), order.getConfirmedAt(),
                order.getPreparationStartedAt(), order.getReadyAt(), order.getDispatchedAt(), order.getCompletedAt(),
                order.getCancelledAt(), order.getCancelReason(), order.getVersion());
    }
}
