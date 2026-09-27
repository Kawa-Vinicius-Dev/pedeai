package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.dto.MarketplaceCancellationRequest;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.repository.OutboundActionRepository;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.order.service.OrderStatusService;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceOrderServiceTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final CurrentUser CASHIER = new CurrentUser(USER_ID, STORE_ID, Role.CASHIER, "Caio");
    private static final MarketplaceCancellationRequest REQUEST =
            new MarketplaceCancellationRequest("503", "Item indisponível");

    private final OrderService orders = mock(OrderService.class);
    private final OrderStatusService statuses = mock(OrderStatusService.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final OutboundActionRepository actions = mock(OutboundActionRepository.class);

    private MarketplaceOrderService service(IfoodProperties properties) {
        return new MarketplaceOrderService(orders, statuses, outbox, actions, mock(IfoodClient.class), properties,
                CLOCK);
    }

    private static OrderResponse order(OrderSource source, OrderStatus status) {
        return new OrderResponse(ORDER_ID, 42, LocalDate.of(2026, 9, 27), OrderType.DELIVERY, source, status, null,
                "Rita", null, null, null, List.of(), 0, 0, 0, 0, 0, 0, NOW, NOW, null, null, null, null, null, null, 0,
                "7391", null, source == OrderSource.PEDEAI ? null : "ifood-1");
    }

    private void stored(OrderResponse order) {
        when(orders.get(STORE_ID, ORDER_ID)).thenReturn(order);
        when(actions.findAllByOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of());
        when(outbox.requestCancellation(eq(STORE_ID), any(), anyString(), anyString())).thenReturn(
                new OutboundAction(STORE_ID, OrderSource.IFOOD, ORDER_ID, "ifood-1",
                        OutboundAction.Action.REQUEST_CANCELLATION, "{}", NOW));
    }

    @Test
    void withTheIntegrationOnTheCancellationIsOnlyRequested() {
        stored(order(OrderSource.IFOOD, OrderStatus.CONFIRMED));

        var response = service(new IfoodProperties(false, "x", null, null, "/p", "/a", true))
                .requestCancellation(CASHIER, ORDER_ID, REQUEST);

        assertThat(response.status()).isEqualTo(OutboundAction.Status.PENDING);
        verify(statuses, never()).cancelWithoutPlatform(any(), any(), anyString());
    }

    @Test
    void withTheIntegrationOffTheOrderIsCancelledHereSoItIsNotStuck() {
        stored(order(OrderSource.IFOOD, OrderStatus.CONFIRMED));

        var response = service(new IfoodProperties(false, "x", null, null, "/p", "/a", false))
                .requestCancellation(CASHIER, ORDER_ID, REQUEST);

        verify(statuses).cancelWithoutPlatform(CASHIER, ORDER_ID, "Item indisponível");
        assertThat(response.status()).isEqualTo(OutboundAction.Status.SKIPPED);
    }

    @Test
    void ownOrdersAndRepeatedRequestsAreRefused() {
        stored(order(OrderSource.PEDEAI, OrderStatus.CONFIRMED));
        IfoodProperties simulator = new IfoodProperties(false, "x", null, null, "/p", "/a", true);
        assertThatThrownBy(() -> service(simulator).requestCancellation(CASHIER, ORDER_ID, REQUEST))
                .isInstanceOf(BusinessRuleException.class);

        stored(order(OrderSource.IFOOD, OrderStatus.CONFIRMED));
        when(actions.findAllByOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of(new OutboundAction(STORE_ID,
                OrderSource.IFOOD, ORDER_ID, "ifood-1", OutboundAction.Action.REQUEST_CANCELLATION, "{}", NOW)));
        assertThatThrownBy(() -> service(simulator).requestCancellation(CASHIER, ORDER_ID, REQUEST))
                .isInstanceOf(ConflictException.class);
    }
}
