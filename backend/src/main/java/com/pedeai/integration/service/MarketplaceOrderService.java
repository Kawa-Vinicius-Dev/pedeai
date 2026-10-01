package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.dto.CancellationReasonResponse;
import com.pedeai.integration.dto.MarketplaceCancellationRequest;
import com.pedeai.integration.dto.OutboundActionResponse;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.repository.OutboundActionRepository;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.order.service.OrderStatusService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** O pedido do iFood na tela: sincronização, motivos de cancelamento e pedido de cancelamento. */
@Service
public class MarketplaceOrderService {
    static final String NOT_MARKETPLACE = "Este pedido não veio de um marketplace.";
    static final String ALREADY_FINAL = "Este pedido já foi concluído ou cancelado.";
    static final String CANCELLATION_PENDING = "O cancelamento deste pedido já foi solicitado ao iFood.";
    static final String ACTION_NOT_FOUND = "Sincronização não encontrada.";
    static final String CANNOT_RETRY = "Só dá para tentar de novo o que falhou.";

    /**
     * Motivos que o iFood costuma aceitar. Só o simulador usa esta lista; com credenciais, os motivos vêm do iFood
     * para aquele pedido, naquele momento.
     */
    static final List<CancellationReasonResponse> SIMULATED_REASONS = List.of(
            new CancellationReasonResponse("501", "Problemas de sistema"),
            new CancellationReasonResponse("502", "Pedido em duplicidade"),
            new CancellationReasonResponse("503", "Item indisponível"),
            new CancellationReasonResponse("504", "Restaurante sem motoboy"),
            new CancellationReasonResponse("506", "Pedido fora da área de entrega"),
            new CancellationReasonResponse("509", "Dificuldades internas do restaurante"));

    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final OutboxService outbox;
    private final OutboundActionRepository actions;
    private final IfoodClient ifood;
    private final IfoodProperties properties;
    private final Clock clock;

    public MarketplaceOrderService(OrderService orderService, OrderStatusService orderStatusService,
                                   OutboxService outbox, OutboundActionRepository actions, IfoodClient ifood,
                                   IfoodProperties properties, Clock clock) {
        this.orderService = orderService;
        this.orderStatusService = orderStatusService;
        this.outbox = outbox;
        this.actions = actions;
        this.ifood = ifood;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OutboundActionResponse> sync(UUID storeId, UUID orderId) {
        orderService.get(storeId, orderId);
        return actions.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream().map(OutboundActionResponse::from)
                .toList();
    }

    public List<CancellationReasonResponse> cancellationReasons(UUID storeId, UUID orderId) {
        OrderResponse order = marketplaceOrder(storeId, orderId);
        if (!properties.configured()) {
            if (properties.simulator()) {
                return SIMULATED_REASONS;
            }
            throw new BusinessRuleException(ConnectionService.NOT_CONFIGURED);
        }
        return ifood.cancellationReasons(order.externalId()).stream()
                .map(reason -> new CancellationReasonResponse(reason.code(), reason.description())).toList();
    }

    /**
     * O pedido só é cancelado aqui quando o iFood confirmar, pelo evento de cancelado. Com a integração desligada no
     * servidor, não há a quem pedir: cancela aqui mesmo, senão o pedido ficaria aberto para sempre.
     */
    @Transactional
    public OutboundActionResponse requestCancellation(CurrentUser user, UUID orderId,
                                                      MarketplaceCancellationRequest request) {
        UUID storeId = user.storeId();
        OrderResponse order = marketplaceOrder(storeId, orderId);
        if (!properties.configured() && !properties.simulator()) {
            orderStatusService.cancelWithoutPlatform(user, orderId, request.description().trim());
            OutboundAction skipped = outbox.requestCancellation(storeId, order, request.code().trim(),
                    request.description().trim());
            skipped.skipped("Integração desligada: cancelado só no PedeAí. Confira no Portal do Parceiro.",
                    Instant.now(clock));
            return OutboundActionResponse.from(skipped);
        }
        boolean pending = actions.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .anyMatch(action -> action.getAction() == OutboundAction.Action.REQUEST_CANCELLATION
                        && (action.getStatus() == OutboundAction.Status.PENDING
                        || action.getStatus() == OutboundAction.Status.DONE));
        if (pending) {
            throw new ConflictException(CANCELLATION_PENDING);
        }
        return OutboundActionResponse.from(outbox.requestCancellation(storeId, order, request.code().trim(),
                request.description().trim()));
    }

    /** Uma pessoa manda tentar de novo o que falhou (por exemplo, o iFood fora do ar por muito tempo). */
    @Transactional
    public OutboundActionResponse retry(UUID storeId, UUID actionId) {
        OutboundAction action = actions.findByIdAndStoreId(actionId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(ACTION_NOT_FOUND));
        if (action.getStatus() != OutboundAction.Status.FAILED) {
            throw new ConflictException(CANNOT_RETRY);
        }
        action.retryNow(Instant.now(clock));
        return OutboundActionResponse.from(action);
    }

    private OrderResponse marketplaceOrder(UUID storeId, UUID orderId) {
        OrderResponse order = orderService.get(storeId, orderId);
        if (order.source() == OrderSource.PEDEAI || order.externalId() == null) {
            throw new BusinessRuleException(NOT_MARKETPLACE);
        }
        if (order.status().isFinal()) {
            throw new BusinessRuleException(ALREADY_FINAL);
        }
        return order;
    }
}
