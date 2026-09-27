package com.pedeai.integration.service;

import com.pedeai.integration.domain.InboundEvent;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.ifood.IfoodOrderMapper;
import com.pedeai.integration.repository.InboundEventRepository;
import com.pedeai.integration.repository.MarketplaceConnectionRepository;
import com.pedeai.integration.repository.OutboundActionRepository;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.order.service.OrderStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa um evento do iFood, cada um na sua transação: pedido novo é importado, mudança de status é aplicada.
 * O mapeamento de códigos está em docs/05-integracoes.md#mapeamento-de-status.
 */
@Service
public class InboundEventHandler {
    /** Código curto e código completo do iFood para o status do PedeAí. */
    static final Map<String, OrderStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry("CFM", OrderStatus.CONFIRMED), Map.entry("CONFIRMED", OrderStatus.CONFIRMED),
            Map.entry("RTP", OrderStatus.READY), Map.entry("READY_TO_PICKUP", OrderStatus.READY),
            Map.entry("DSP", OrderStatus.DISPATCHED), Map.entry("DISPATCHED", OrderStatus.DISPATCHED),
            Map.entry("CON", OrderStatus.COMPLETED), Map.entry("CONCLUDED", OrderStatus.COMPLETED),
            Map.entry("CAN", OrderStatus.CANCELLED), Map.entry("CANCELLED", OrderStatus.CANCELLED));
    static final String CANCELLATION_REFUSED = "O iFood não aceitou o pedido de cancelamento.";

    private final InboundEventRepository events;
    private final MarketplaceConnectionRepository connections;
    private final OutboundActionRepository actions;
    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final IfoodClient ifood;
    private final IfoodOrderMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;

    public InboundEventHandler(InboundEventRepository events, MarketplaceConnectionRepository connections,
                               OutboundActionRepository actions, OrderService orderService,
                               OrderStatusService orderStatusService, IfoodClient ifood, IfoodOrderMapper mapper,
                               ObjectMapper json, Clock clock) {
        this.events = events;
        this.connections = connections;
        this.actions = actions;
        this.orderService = orderService;
        this.orderStatusService = orderStatusService;
        this.ifood = ifood;
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void handle(UUID eventId) {
        InboundEvent event = events.findById(eventId).orElseThrow();
        Instant now = Instant.now(clock);
        Optional<MarketplaceConnection> found = event.getExternalMerchantId() == null ? Optional.empty()
                : connections.findByProviderAndExternalMerchantId(event.getProvider(), event.getExternalMerchantId());
        if (found.isEmpty()) {
            event.ignored(null, "Merchant sem vínculo com uma loja do PedeAí.", now);
            return;
        }
        MarketplaceConnection connection = found.get();
        UUID storeId = connection.getStoreId();
        connection.eventReceived(now);
        if (connection.getStatus() != MarketplaceConnection.Status.ACTIVE) {
            event.ignored(storeId, "Integração pausada na loja.", now);
            return;
        }
        String code = event.getEventCode();
        JsonNode payload = json.readTree(event.getPayload());
        if ("PLC".equals(code) || "PLACED".equals(code)) {
            ensureImported(connection, event.getExternalOrderId(), payload);
        } else if (STATUS_BY_CODE.containsKey(code)) {
            OrderResponse order = ensureImported(connection, event.getExternalOrderId(), payload);
            orderStatusService.applyFromMarketplace(storeId, order.id(), STATUS_BY_CODE.get(code),
                    STATUS_BY_CODE.get(code) == OrderStatus.CANCELLED ? cancelReason(payload) : null);
        } else if ("CARF".equals(code) || "CANCELLATION_REQUEST_FAILED".equals(code)) {
            orderService.findExternal(storeId, OrderSource.IFOOD, event.getExternalOrderId())
                    .ifPresent(order -> actions.findAllByOrderIdOrderByCreatedAtAsc(order.id()).stream()
                            .filter(action -> action.getAction() == OutboundAction.Action.REQUEST_CANCELLATION)
                            .reduce((first, second) -> second)
                            .ifPresent(action -> action.rejected(CANCELLATION_REFUSED, now)));
        } else {
            event.ignored(storeId, "Evento " + code + " sem ação no PedeAí.", now);
            return;
        }
        event.processed(storeId, now);
    }

    /** Numa transação nova: a do processamento pode ter sido desfeita pelo próprio erro. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryLater(UUID eventId, String error) {
        events.findById(eventId).ifPresent(event -> event.retryLater(error, Instant.now(clock)));
    }

    /**
     * Pedido novo, ou evento de status que chegou antes do pedido: busca os detalhes e importa. O simulador manda o
     * pedido dentro do próprio evento.
     */
    private OrderResponse ensureImported(MarketplaceConnection connection, String externalOrderId, JsonNode payload) {
        Optional<OrderResponse> existing = orderService.findExternal(connection.getStoreId(), OrderSource.IFOOD,
                externalOrderId);
        if (existing.isPresent()) {
            return existing.get();
        }
        JsonNode details = payload.has("order") ? payload.get("order") : ifood.order(externalOrderId);
        return orderService.importFromMarketplace(connection.getStoreId(),
                mapper.map(connection.getStoreId(), details, connection.isAutoConfirm()));
    }

    private static String cancelReason(JsonNode payload) {
        JsonNode metadata = payload.path("metadata");
        for (String field : new String[]{"CANCEL_REASON", "reason", "cancelReason", "details"}) {
            String reason = metadata.path(field).asString("");
            if (!reason.isBlank()) {
                return "iFood: " + (reason.length() > 290 ? reason.substring(0, 290) : reason);
            }
        }
        return "Cancelado pelo iFood";
    }
}
