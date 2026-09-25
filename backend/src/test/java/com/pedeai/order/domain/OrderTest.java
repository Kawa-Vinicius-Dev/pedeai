package com.pedeai.order.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-25T19:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(600);

    @Test
    void totalIsItemsMinusDiscountPlusDeliveryFee() {
        // 2 pizzas de R$ 52,90 (sabor mais caro) + 1 refrigerante de R$ 7,00 = R$ 112,80
        Order order = order(OrderType.DELIVERY, List.of(item("Pizza Grande", 2, 0, 5290), item("Refrigerante", 1, 700, 0)),
                1000, 800);

        assertThat(order.getSubtotalCents()).isEqualTo(11_280);
        assertThat(order.getDiscountCents()).isEqualTo(1000);
        assertThat(order.getDeliveryFeeCents()).isEqualTo(800);
        assertThat(order.getTotalCents()).isEqualTo(11_280 - 1000 + 800);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(order.getSource()).isEqualTo(OrderSource.PEDEAI);
    }

    @Test
    void discountCannotExceedTheItems() {
        assertThatThrownBy(() -> order(OrderType.TAKEOUT, List.of(item("Suco", 1, 800, 0)), 900, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Order.DISCOUNT_TOO_HIGH);
    }

    @Test
    void walksTheLifecycleAndStampsEachStep() {
        Order order = order(OrderType.DELIVERY, List.of(item("Pizza", 1, 4590, 0)), 0, 500);

        order.advanceTo(OrderStatus.CONFIRMED, NOW);
        order.advanceTo(OrderStatus.IN_PREPARATION, NOW);
        order.advanceTo(OrderStatus.READY, LATER);
        order.advanceTo(OrderStatus.DISPATCHED, LATER);
        order.advanceTo(OrderStatus.COMPLETED, LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getConfirmedAt()).isEqualTo(NOW);
        assertThat(order.getPreparationStartedAt()).isEqualTo(NOW);
        assertThat(order.getReadyAt()).isEqualTo(LATER);
        assertThat(order.getDispatchedAt()).isEqualTo(LATER);
        assertThat(order.getCompletedAt()).isEqualTo(LATER);
    }

    @Test
    void canSkipStepsButNeverGoesBack() {
        Order order = order(OrderType.TAKEOUT, List.of(item("Refrigerante", 1, 700, 0)), 0, 0);
        order.advanceTo(OrderStatus.CONFIRMED, NOW);

        // Pedido só de bebidas: de confirmado direto para pronto.
        assertThat(order.advanceTo(OrderStatus.READY, NOW)).isTrue();
        assertThat(order.getPreparationStartedAt()).isNull();
        assertThatThrownBy(() -> order.advanceTo(OrderStatus.IN_PREPARATION, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Order.ONLY_FORWARD);
    }

    @Test
    void sameStatusAgainChangesNothing() {
        Order order = order(OrderType.TAKEOUT, List.of(item("Refrigerante", 1, 700, 0)), 0, 0);
        order.advanceTo(OrderStatus.CONFIRMED, NOW);

        assertThat(order.advanceTo(OrderStatus.CONFIRMED, LATER)).isFalse();
        assertThat(order.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void onlyDeliveryIsDispatched() {
        Order order = order(OrderType.TAKEOUT, List.of(item("Pizza", 1, 4590, 0)), 0, 0);
        order.advanceTo(OrderStatus.READY, NOW);

        assertThatThrownBy(() -> order.advanceTo(OrderStatus.DISPATCHED, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Order.DISPATCH_ONLY_DELIVERY);
    }

    @Test
    void cancelledAndCompletedOrdersStopMoving() {
        Order cancelled = order(OrderType.TAKEOUT, List.of(item("Pizza", 1, 4590, 0)), 0, 0);
        cancelled.cancel("Cliente desistiu", NOW);
        assertThatThrownBy(() -> cancelled.advanceTo(OrderStatus.CONFIRMED, NOW))
                .hasMessage(Order.CANCELLED_IS_FINAL);
        assertThat(cancelled.cancel("de novo", LATER)).isFalse();
        assertThat(cancelled.getCancelReason()).isEqualTo("Cliente desistiu");
        assertThat(cancelled.getCancelledAt()).isEqualTo(NOW);

        Order completed = order(OrderType.TAKEOUT, List.of(item("Pizza", 1, 4590, 0)), 0, 0);
        completed.advanceTo(OrderStatus.COMPLETED, NOW);
        assertThatThrownBy(() -> completed.advanceTo(OrderStatus.READY, NOW)).hasMessage(Order.COMPLETED_IS_FINAL);
        // Correção de lançamento: concluído ainda pode ser cancelado (a regra de quem pode fica no serviço).
        assertThat(completed.cancel("Lançado em duplicidade", LATER)).isTrue();
    }

    @Test
    void itemTotalMultipliesBaseAndOptionsByQuantity() {
        OrderItem item = item("Pizza Grande", 3, 0, 5290);

        assertThat(item.getTotalCents()).isEqualTo(3 * 5290);
    }

    private static Order order(OrderType type, List<OrderItem> items, long discount, long fee) {
        DeliveryAddress address = type == OrderType.DELIVERY
                ? new DeliveryAddress("Rua A", "10", null, "Centro", null, null, null, null)
                : null;
        return Order.placeOwn(STORE, LocalDate.of(2026, 9, 25), 1, type,
                new OrderCustomer(null, "Maria", null), address, null, items, discount, fee, UUID.randomUUID(), NOW);
    }

    private static OrderItem item(String name, int quantity, long base, long options) {
        return new OrderItem(STORE, UUID.randomUUID(), null, name, null, quantity, base, options, null, List.of(), 0);
    }
}
