package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.domain.OutboundAction.Action;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.repository.MarketplaceConnectionRepository;
import com.pedeai.integration.repository.OutboundActionRepository;
import com.pedeai.order.domain.ActorType;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.event.OrderStatusChanged;
import com.pedeai.order.service.OrderService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saída para a plataforma (docs/05-integracoes.md#saída-status): a mudança de status local grava a ação na mesma
 * transação, e o envio acontece depois, com nova tentativa. O que veio da plataforma não volta para ela.
 */
@Service
public class OutboxService {
    private static final Map<Action, String> IFOOD_PATH = Map.of(
            Action.CONFIRM, "confirm", Action.START_PREPARATION, "startPreparation", Action.READY, "readyToPickup",
            Action.DISPATCH, "dispatch");

    private final OutboundActionRepository actions;
    private final MarketplaceConnectionRepository connections;
    private final OrderService orderService;
    private final InboundService inbound;
    private final IfoodClient ifood;
    private final IfoodProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public OutboxService(OutboundActionRepository actions, MarketplaceConnectionRepository connections,
                         OrderService orderService, InboundService inbound, IfoodClient ifood,
                         IfoodProperties properties, ObjectMapper json, Clock clock) {
        this.actions = actions;
        this.connections = connections;
        this.orderService = orderService;
        this.inbound = inbound;
        this.ifood = ifood;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    /** Aceite automático: o pedido nasceu confirmado aqui, e o iFood precisa saber. */
    @EventListener
    public void onOrderCreated(OrderCreated event) {
        if (event.source() == OrderSource.IFOOD && event.status() != OrderStatus.RECEIVED) {
            OrderResponse order = orderService.get(event.storeId(), event.orderId());
            enqueue(event.storeId(), order, Action.CONFIRM, null);
        }
    }

    /** Mudança feita na loja num pedido do iFood. Pulou o aceite? O aceite sai antes. */
    @EventListener
    public void onStatusChanged(OrderStatusChanged event) {
        if (event.actor() == ActorType.MARKETPLACE) {
            return;
        }
        OrderResponse order = orderService.get(event.storeId(), event.orderId());
        if (order.source() != OrderSource.IFOOD || order.externalId() == null) {
            return;
        }
        List<Action> toSend = new ArrayList<>();
        if (event.from() == OrderStatus.RECEIVED && event.to() != OrderStatus.CONFIRMED
                && event.to() != OrderStatus.CANCELLED) {
            toSend.add(Action.CONFIRM);
        }
        switch (event.to()) {
            case CONFIRMED -> toSend.add(Action.CONFIRM);
            case IN_PREPARATION -> toSend.add(Action.START_PREPARATION);
            case READY -> toSend.add(Action.READY);
            case DISPATCHED -> toSend.add(Action.DISPATCH);
            default -> {
            }
        }
        toSend.forEach(action -> enqueue(event.storeId(), order, action, null));
    }

    /** Pedido de cancelamento: só cancela aqui quando o iFood confirmar (evento de cancelado). */
    @Transactional
    public OutboundAction requestCancellation(UUID storeId, OrderResponse order, String code, String description) {
        return enqueue(storeId, order, Action.REQUEST_CANCELLATION,
                json.writeValueAsString(Map.of("code", code, "description", description)));
    }

    /**
     * Envia uma ação. Rede ou 5xx: nova tentativa. 4xx: a plataforma recusou e repetir não adianta. Sem credenciais,
     * o simulador faz o papel do iFood; sem nenhum dos dois, a ação é descartada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(UUID actionId) {
        OutboundAction action = actions.findById(actionId).orElseThrow();
        Instant now = Instant.now(clock);
        if (action.getStatus() != OutboundAction.Status.PENDING) {
            return;
        }
        if (!properties.configured()) {
            if (properties.simulator()) {
                simulate(action);
                action.done(now);
            } else {
                action.skipped("Integração com o iFood desligada no servidor.", now);
            }
            return;
        }
        try {
            if (action.getAction() == Action.REQUEST_CANCELLATION) {
                JsonNode payload = json.readTree(action.getPayload());
                ifood.requestCancellation(action.getExternalOrderId(), payload.path("code").asString(),
                        payload.path("description").asString());
            } else {
                ifood.orderAction(action.getExternalOrderId(), IFOOD_PATH.get(action.getAction()));
            }
            action.done(now);
        } catch (IfoodClient.IfoodApiException e) {
            if (e.retryable()) {
                action.retryLater(e.getMessage(), now);
            } else {
                action.rejected(e.getMessage(), now);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> due(int limit) {
        Instant now = Instant.now(clock);
        return actions.findAllByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OutboundAction.Status.PENDING,
                        now, org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                // As ações de um pedido saem em ordem: só a mais antiga pendente de cada pedido anda.
                .filter(action -> actions.findFirstByOrderIdAndStatusOrderByCreatedAtAsc(action.getOrderId(),
                        OutboundAction.Status.PENDING).map(first -> first.getId().equals(action.getId())).orElse(false))
                .map(OutboundAction::getId)
                .toList();
    }

    private OutboundAction enqueue(UUID storeId, OrderResponse order, Action action, String payload) {
        return actions.save(new OutboundAction(storeId, OrderSource.IFOOD, order.id(), order.externalId(),
                action, payload, Instant.now(clock)));
    }

    /** O simulador responde como o iFood: o pedido de cancelamento aceito volta como evento de cancelado. */
    private void simulate(OutboundAction action) {
        if (action.getAction() != Action.REQUEST_CANCELLATION) {
            return;
        }
        String merchantId = connections.findAllByStoreIdOrderByCreatedAtAsc(action.getStoreId()).stream()
                .filter(connection -> connection.getProvider() == OrderSource.IFOOD)
                .map(connection -> connection.getExternalMerchantId()).findFirst().orElse(null);
        JsonNode payload = json.readTree(action.getPayload());
        inbound.record(OrderSource.IFOOD, json.valueToTree(Map.of(
                "id", "sim-can-" + action.getId(), "code", "CAN", "fullCode", "CANCELLED",
                "orderId", action.getExternalOrderId(), "merchantId", merchantId == null ? "" : merchantId,
                "metadata", Map.of("CANCEL_REASON", payload.path("description").asString()))));
    }
}
