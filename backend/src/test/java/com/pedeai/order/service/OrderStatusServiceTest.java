package com.pedeai.order.service;

import com.pedeai.order.domain.ActorType;
import com.pedeai.order.domain.DeliveryAddress;
import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderCustomer;
import com.pedeai.order.domain.OrderItem;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderStatusHistory;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.ChangeOrderStatusRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.event.OrderStatusChanged;
import com.pedeai.order.repository.OrderRepository;
import com.pedeai.order.repository.OrderStatusHistoryRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private ApplicationEventPublisher events;

    private OrderStatusService service() {
        return new OrderStatusService(orderRepository, historyRepository, events, CLOCK);
    }

    @Test
    void advancesRecordsTheTimelineAndWarnsTheScreens() {
        Order order = stored(OrderStatus.CONFIRMED);

        OrderResponse response = service().change(user(Role.CASHIER), order.getId(),
                new ChangeOrderStatusRequest(OrderStatus.READY, null, 0L));

        assertThat(response.status()).isEqualTo(OrderStatus.READY);
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(history.getValue().getToStatus()).isEqualTo(OrderStatus.READY);
        assertThat(history.getValue().getActorType()).isEqualTo(ActorType.USER);
        assertThat(history.getValue().getActorName()).isEqualTo("Ana");
        verify(orderRepository).flush();
        verify(events).publishEvent(new OrderStatusChanged(STORE_ID, order.getId(), 7, OrderStatus.CONFIRMED,
                OrderStatus.READY, 0L));
    }

    @Test
    void sameStatusAgainIsANoOpEvenFromAStaleScreen() {
        Order order = stored(OrderStatus.READY);

        OrderResponse response = service().change(user(Role.CASHIER), order.getId(),
                new ChangeOrderStatusRequest(OrderStatus.READY, null, 99L));

        assertThat(response.status()).isEqualTo(OrderStatus.READY);
        verifyNoInteractions(historyRepository, events);
    }

    @Test
    void staleScreenGetsConflict() {
        Order order = stored(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> service().change(user(Role.CASHIER), order.getId(),
                new ChangeOrderStatusRequest(OrderStatus.READY, null, 3L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(OrderStatusService.STALE);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void kitchenOnlyStartsAndFinishesPreparation() {
        Order order = stored(OrderStatus.CONFIRMED);

        assertThat(service().change(user(Role.KITCHEN), order.getId(),
                new ChangeOrderStatusRequest(OrderStatus.IN_PREPARATION, null, null)).status())
                .isEqualTo(OrderStatus.IN_PREPARATION);
        assertThatThrownBy(() -> service().change(user(Role.KITCHEN), order.getId(),
                new ChangeOrderStatusRequest(OrderStatus.COMPLETED, null, null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(OrderStatusService.KITCHEN_ONLY_PREPARATION);
        assertThatThrownBy(() -> service().change(user(Role.KITCHEN), order.getId(),
                new ChangeOrderStatusRequest(OrderStatus.CANCELLED, "acabou", null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(OrderStatusService.KITCHEN_CANNOT_CANCEL);
    }

    @Test
    void cancellingNeedsAReason() {
        Order order = stored(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> service().change(user(Role.CASHIER), order.getId(),
                new ChangeOrderStatusRequest(OrderStatus.CANCELLED, "   ", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(OrderStatusService.REASON_REQUIRED);
    }

    @Test
    void cashierCancelsBeforePreparationButNotAfter() {
        Order confirmed = stored(OrderStatus.CONFIRMED);
        OrderResponse cancelled = service().change(user(Role.CASHIER), confirmed.getId(),
                new ChangeOrderStatusRequest(OrderStatus.CANCELLED, " Cliente desistiu ", null));
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Cliente desistiu");

        Order preparing = stored(OrderStatus.IN_PREPARATION);
        assertThatThrownBy(() -> service().change(user(Role.CASHIER), preparing.getId(),
                new ChangeOrderStatusRequest(OrderStatus.CANCELLED, "Cliente desistiu", null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(OrderStatusService.CANCEL_AFTER_PREPARATION);
        assertThat(service().change(user(Role.MANAGER), preparing.getId(),
                new ChangeOrderStatusRequest(OrderStatus.CANCELLED, "Cliente desistiu", null)).status())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void completedOwnOrderCanBeCorrectedByAManager() {
        Order completed = stored(OrderStatus.COMPLETED);

        assertThatThrownBy(() -> service().change(user(Role.CASHIER), completed.getId(),
                new ChangeOrderStatusRequest(OrderStatus.CANCELLED, "Lançado duas vezes", null)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(service().change(user(Role.OWNER), completed.getId(),
                new ChangeOrderStatusRequest(OrderStatus.CANCELLED, "Lançado duas vezes", null)).status())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void orderOfAnotherStoreIsNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdAndStoreId(id, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().change(user(Role.OWNER), id,
                new ChangeOrderStatusRequest(OrderStatus.READY, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Order stored(OrderStatus status) {
        Instant now = Instant.parse("2026-09-24T11:00:00Z");
        Order order = Order.placeOwn(STORE_ID, LocalDate.of(2026, 9, 24), 7, OrderType.DELIVERY,
                new OrderCustomer(null, "Maria", null),
                new DeliveryAddress("Rua A", "1", null, "Centro", null, null, null, null), null,
                List.of(new OrderItem(STORE_ID, UUID.randomUUID(), null, "Pizza", null, 1, 4590, 0, null, List.of(), 0)),
                0, 800, USER_ID, now);
        if (status == OrderStatus.CANCELLED) {
            order.cancel("x", now);
        } else if (status != OrderStatus.RECEIVED) {
            order.advanceTo(status, now);
        }
        when(orderRepository.findByIdAndStoreId(order.getId(), STORE_ID)).thenReturn(Optional.of(order));
        return order;
    }

    private static CurrentUser user(Role role) {
        return new CurrentUser(USER_ID, STORE_ID, role, "Ana");
    }
}
