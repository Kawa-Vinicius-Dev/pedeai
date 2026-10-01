package com.pedeai.integration.service;

import com.pedeai.integration.domain.InboundEvent;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.ifood.IfoodOrderMapper;
import com.pedeai.integration.opendelivery.OpenDeliveryClient;
import com.pedeai.integration.opendelivery.OpenDeliveryOrderMapper;
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
 * Processa um evento do iFood ou de um app Open Delivery, cada um na sua transação: pedido novo é importado, mudança de status é aplicada.
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
            Map.entry("CAN", OrderStatus.CANCELLED), Map.entry("CANCELLED", OrderStatus.CANCELLED),
            // Open Delivery (os outros nomes são os mesmos do iFood).
            Map.entry("PREPARING", OrderStatus.IN_PREPARATION), Map.entry("READY_FOR_PICKUP", OrderStatus.READY),
            Map.entry("DELIVERED", OrderStatus.COMPLETED));
    static final String CANCELLATION_REFUSED = "A plataforma não aceitou o pedido de cancelamento.";

    private final InboundEventRepository events;
    private final MarketplaceConnectionRepository connections;
    private final OutboundActionRepository actions;
    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final IfoodClient ifood;
    private final IfoodOrderMapper mapper;
    private final OpenDeliveryClient openDelivery;
    private final OpenDeliveryOrderMapper openDeliveryMapper;
    private final Platforms platforms;
    private final DisputeService disputes;
    private final ObjectMapper json;
    private final Clock clock;

    public InboundEventHandler(InboundEventRepository events, MarketplaceConnectionRepository connections,
                               OutboundActionRepository actions, OrderService orderService,
                               OrderStatusService orderStatusService, IfoodClient ifood, IfoodOrderMapper mapper,
                               OpenDeliveryClient openDelivery, OpenDeliveryOrderMapper openDeliveryMapper,
                               Platforms platforms, DisputeService disputes, ObjectMapper json, Clock clock) {
        this.events = events;
        this.connections = connections;
        this.actions = actions;
        this.orderService = orderService;
        this.orderStatusService = orderStatusService;
        this.ifood = ifood;
        this.mapper = mapper;
        this.openDelivery = openDelivery;
        this.openDeliveryMapper = openDeliveryMapper;
        this.platforms = platforms;
        this.disputes = disputes;
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
        if ("PLC".equals(code) || "PLACED".equals(code) || "CREATED".equals(code)) {
            ensureImported(connection, event.getExternalOrderId(), payload);
        } else if (STATUS_BY_CODE.containsKey(code)) {
            OrderResponse order = ensureImported(connection, event.getExternalOrderId(), payload);
            orderStatusService.applyFromMarketplace(storeId, order.id(), STATUS_BY_CODE.get(code),
                    STATUS_BY_CODE.get(code) == OrderStatus.CANCELLED
                            ? cancelReason(platforms.label(event.getProvider()), payload) : null);
            if (STATUS_BY_CODE.get(code).isFinal()) {
                disputes.orderFinished(order.id());
            }
        } else if ("ORDER_CANCELLATION_REQUEST".equals(code) || "HSD".equals(code)
                || "HANDSHAKE_DISPUTE".equals(code)) {
            // O cliente pediu o cancelamento pelo app: a loja aceita ou recusa no PedeAí, no prazo do app.
            OrderResponse order = ensureImported(connection, event.getExternalOrderId(), payload);
            JsonNode metadata = payload.path("metadata");
            disputes.opened(storeId, order.id(), event.getProvider(),
                    metadata.path("disputeId").asString(event.getExternalEventId()),
                    metadata.path("action").asString("CANCELLATION"),
                    firstText(metadata, "message", "reason", "CANCEL_REASON"), instant(metadata.path("expiresAt")));
        } else if ("HSS".equals(code) || "HANDSHAKE_SETTLEMENT".equals(code)) {
            JsonNode metadata = payload.path("metadata");
            disputes.closed(event.getProvider(), metadata.path("disputeId").asString(event.getExternalEventId()));
        } else if ("CARF".equals(code) || "CANCELLATION_REQUEST_FAILED".equals(code)
                || "CANCELLATION_REQUEST_DENIED".equals(code)) {
            orderService.findExternal(storeId, event.getProvider(), event.getExternalOrderId())
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
    /**
     * Guarda o erro no evento e no vínculo da loja, para aparecer na tela de integrações: um pedido que não entra
     * precisa ser visto antes de o iFood cancelá-lo por falta de aceite.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryLater(UUID eventId, String error) {
        Instant now = Instant.now(clock);
        events.findById(eventId).ifPresent(event -> {
            event.retryLater(error, now);
            if (event.getExternalMerchantId() != null) {
                connections.findByProviderAndExternalMerchantId(event.getProvider(), event.getExternalMerchantId())
                        .ifPresent(connection -> connection.failed("Evento " + event.getEventCode() + " do pedido "
                                + event.getExternalOrderId() + " não entrou: " + error, now));
            }
        });
    }

    /**
     * Pedido novo, ou evento de status que chegou antes do pedido: busca os detalhes e importa. O simulador manda o
     * pedido dentro do próprio evento.
     */
    private OrderResponse ensureImported(MarketplaceConnection connection, String externalOrderId, JsonNode payload) {
        OrderSource provider = connection.getProvider();
        Optional<OrderResponse> existing = orderService.findExternal(connection.getStoreId(), provider,
                externalOrderId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (provider == OrderSource.IFOOD) {
            JsonNode details = payload.has("order") ? payload.get("order") : ifood.order(externalOrderId);
            return orderService.importFromMarketplace(connection.getStoreId(),
                    mapper.map(connection.getStoreId(), details, connection.isAutoConfirm()));
        }
        JsonNode details = payload.has("order") ? payload.get("order") : openDelivery.order(provider, externalOrderId);
        return orderService.importFromMarketplace(connection.getStoreId(), openDeliveryMapper.map(
                connection.getStoreId(), provider, platforms.label(provider), details, connection.isAutoConfirm()));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asString("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Instant instant(JsonNode value) {
        try {
            return value.isMissingNode() || value.isNull() ? null : Instant.parse(value.asString());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static String cancelReason(String platform, JsonNode payload) {
        JsonNode metadata = payload.path("metadata");
        for (String field : new String[]{"CANCEL_REASON", "reason", "cancelReason", "details"}) {
            String reason = metadata.path(field).asString("");
            if (!reason.isBlank()) {
                return platform + ": " + (reason.length() > 280 ? reason.substring(0, 280) : reason);
            }
        }
        return "Cancelado pelo " + platform;
    }
}
