package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.support.IntegrationTestProperties;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.domain.OutboundAction.Action;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.opendelivery.OpenDeliveryClient;
import com.pedeai.integration.repository.MarketplaceConnectionRepository;
import com.pedeai.integration.repository.OutboundActionRepository;
import com.pedeai.order.domain.ActorType;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.event.OrderStatusChanged;
import com.pedeai.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxServiceTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final IfoodProperties CONFIGURED = IntegrationTestProperties.ifood(true, "http://ifood", "id", "segredo",
            "/p", "/a", false);

    @Mock
    private OutboundActionRepository actions;
    @Mock
    private MarketplaceConnectionRepository connections;
    @Mock
    private OrderService orders;
    @Mock
    private InboundService inbound;
    @Mock
    private IfoodClient ifood;

    private OutboxService service(IfoodProperties properties) {
        return new OutboxService(actions, connections, orders, inbound, ifood, mock(OpenDeliveryClient.class),
                IntegrationTestProperties.platforms(properties), JsonMapper.builder().build(), CLOCK);
    }

    @BeforeEach
    void setUp() {
        when(actions.save(any())).then(returnsFirstArg());
        when(orders.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderSource.IFOOD));
    }

    private static OrderResponse order(OrderSource source) {
        return new OrderResponse(ORDER_ID, 42, LocalDate.of(2026, 9, 27), OrderType.DELIVERY, source,
                OrderStatus.READY, null, "Rita", null, null, null, List.of(), 0, 0, 0, 0, 0, 0, NOW, NOW, null,
                null, null, null, null, null, 0, "7391", null, source == OrderSource.PEDEAI ? null : "ifood-1", null);
    }

    private static OrderStatusChanged changed(OrderStatus from, OrderStatus to, ActorType actor) {
        return new OrderStatusChanged(STORE_ID, ORDER_ID, 42, from, to, 1, actor);
    }

    private List<Action> enqueued() {
        ArgumentCaptor<OutboundAction> captor = ArgumentCaptor.forClass(OutboundAction.class);
        verify(actions, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().map(OutboundAction::getAction).toList();
    }

    @Test
    void readyWithoutAcceptanceSendsTheAcceptanceFirst() {
        service(CONFIGURED).onStatusChanged(changed(OrderStatus.RECEIVED, OrderStatus.READY, ActorType.USER));

        assertThat(enqueued()).containsExactly(Action.CONFIRM, Action.READY);
    }

    @Test
    void whatCameFromThePlatformDoesNotGoBack() {
        service(CONFIGURED).onStatusChanged(changed(OrderStatus.CONFIRMED, OrderStatus.READY, ActorType.MARKETPLACE));

        verify(actions, never()).save(any());
    }

    @Test
    void ownOrdersAreNotSent() {
        when(orders.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderSource.PEDEAI));

        service(CONFIGURED).onStatusChanged(changed(OrderStatus.CONFIRMED, OrderStatus.READY, ActorType.USER));

        verify(actions, never()).save(any());
    }

    @Test
    void ruleErrorIsNotRetriedButServerErrorIs() {
        OutboundAction rejected = new OutboundAction(STORE_ID, OrderSource.IFOOD, ORDER_ID, "ifood-1",
                Action.DISPATCH, null, NOW);
        OutboundAction flaky = new OutboundAction(STORE_ID, OrderSource.IFOOD, ORDER_ID, "ifood-1", Action.READY,
                null, NOW);
        when(actions.findById(rejected.getId())).thenReturn(Optional.of(rejected));
        when(actions.findById(flaky.getId())).thenReturn(Optional.of(flaky));
        doThrow(new IfoodClient.IfoodApiException(400, "entrega é do iFood")).when(ifood)
                .orderAction("ifood-1", "dispatch");
        doThrow(new IfoodClient.IfoodApiException(503, "fora do ar")).when(ifood)
                .orderAction("ifood-1", "readyToPickup");

        service(CONFIGURED).send(rejected.getId());
        service(CONFIGURED).send(flaky.getId());

        assertThat(rejected.getStatus()).isEqualTo(OutboundAction.Status.FAILED);
        assertThat(flaky.getStatus()).isEqualTo(OutboundAction.Status.PENDING);
        assertThat(flaky.getAttempts()).isEqualTo(1);
        assertThat(flaky.getNextAttemptAt()).isAfter(NOW);
    }

    @Test
    void withoutCredentialsTheActionIsDiscardedUnlessTheSimulatorIsOn() {
        OutboundAction action = new OutboundAction(STORE_ID, OrderSource.IFOOD, ORDER_ID, "ifood-1", Action.CONFIRM,
                null, NOW);
        when(actions.findById(action.getId())).thenReturn(Optional.of(action));

        service(IntegrationTestProperties.ifood(false, "http://ifood", null, null, "/p", "/a", false)).send(action.getId());

        assertThat(action.getStatus()).isEqualTo(OutboundAction.Status.SKIPPED);
        verify(ifood, never()).orderAction(anyString(), anyString());
    }
}
