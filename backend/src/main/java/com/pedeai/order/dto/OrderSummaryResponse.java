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
import java.util.stream.Collectors;

/** Pedido no quadro e no histórico. {@code updatedAt} é quando o status mudou por último. */
public record OrderSummaryResponse(
        UUID id,
        int number,
        LocalDate businessDate,
        OrderType type,
        OrderSource source,
        OrderStatus status,
        @Schema(types = {"string", "null"}) String customerName,
        @Schema(types = {"string", "null"}) String deliveryNeighborhood,
        long totalCents,
        int itemCount,
        String itemsSummary,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static OrderSummaryResponse from(Order order) {
        List<OrderItem> items = order.getActiveItems();
        String summary = items.stream()
                .map(item -> item.getQuantity() + "x " + item.getName())
                .collect(Collectors.joining(", "));
        return new OrderSummaryResponse(order.getId(), order.getNumber(), order.getBusinessDate(), order.getType(),
                order.getSource(), order.getStatus(), order.getCustomerName(),
                order.getDeliveryAddress() == null ? null : order.getDeliveryAddress().getNeighborhood(),
                order.getTotalCents(), items.stream().mapToInt(OrderItem::getQuantity).sum(), summary,
                order.getCreatedAt(), order.getUpdatedAt(), order.getVersion());
    }
}
