package com.pedeai.integration.service;

import com.pedeai.integration.domain.MarketplaceDispute;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.dto.CancellationReasonResponse;
import com.pedeai.integration.dto.DisputeAnswerRequest;
import com.pedeai.integration.dto.DisputeResponse;
import com.pedeai.integration.repository.MarketplaceDisputeRepository;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Disputas (docs/05-integracoes.md#disputas): o cliente pediu o cancelamento pelo app; caixa, gerente ou dono aceita
 * ou recusa no prazo, e a resposta vai para o app pela fila de saída. Quem cancela o pedido aqui é o evento de
 * cancelado do app, como em qualquer cancelamento de marketplace.
 */
@Service
public class DisputeService {
    static final String NOT_FOUND = "Pedido de cancelamento não encontrado.";
    static final String REJECT_REASON_REQUIRED = "Escolha o motivo para recusar.";
    /** Motivos de recusa: os dois da especificação Open Delivery; no iFood, vão como texto. */
    static final List<CancellationReasonResponse> REJECT_REASONS = List.of(
            new CancellationReasonResponse("DISH_ALREADY_DONE", "O pedido já está pronto"),
            new CancellationReasonResponse("OUT_FOR_DELIVERY", "O pedido já saiu para entrega"));

    private final MarketplaceDisputeRepository disputes;
    private final OrderService orderService;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public DisputeService(MarketplaceDisputeRepository disputes, OrderService orderService, OutboxService outbox,
                          ObjectMapper json, Clock clock) {
        this.disputes = disputes;
        this.orderService = orderService;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    /** Os que esperam resposta, do mais antigo para o mais novo: é a faixa de alerta do quadro. */
    @Transactional(readOnly = true)
    public List<DisputeResponse> open(UUID storeId) {
        return disputes.findAllByStoreIdAndStatusOrderByCreatedAtAsc(storeId, MarketplaceDispute.Status.OPEN).stream()
                .map(dispute -> toResponse(dispute, orderService.get(storeId, dispute.getOrderId())))
                .toList();
    }

    @Transactional
    public DisputeResponse answer(CurrentUser user, UUID disputeId, DisputeAnswerRequest request) {
        MarketplaceDispute dispute = disputes.findByIdAndStoreId(disputeId, user.storeId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        CancellationReasonResponse reason = null;
        if (!request.accept()) {
            reason = REJECT_REASONS.stream().filter(candidate -> candidate.code().equals(request.rejectCode()))
                    .findFirst().orElseThrow(() -> new BusinessRuleException(REJECT_REASON_REQUIRED));
        }
        dispute.decide(request.accept(), user.userId(), Instant.now(clock));
        OrderResponse order = orderService.get(user.storeId(), dispute.getOrderId());
        String payload = json.writeValueAsString(reason == null
                ? Map.of("disputeId", dispute.getExternalDisputeId())
                : Map.of("disputeId", dispute.getExternalDisputeId(), "code", reason.code(),
                "description", reason.description()));
        outbox.answerDispute(user.storeId(), order, request.accept() ? OutboundAction.Action.ACCEPT_DISPUTE
                : OutboundAction.Action.REJECT_DISPUTE, payload);
        return toResponse(dispute, order);
    }

    /** Chegou do app (evento): grava uma vez só, mesmo que o evento se repita. */
    @Transactional
    public void opened(UUID storeId, UUID orderId, OrderSource provider, String externalDisputeId, String kind,
                       String message, Instant expiresAt) {
        if (disputes.findByProviderAndExternalDisputeId(provider, externalDisputeId).isPresent()) {
            return;
        }
        disputes.save(new MarketplaceDispute(storeId, orderId, provider, externalDisputeId, kind, message, expiresAt,
                Instant.now(clock)));
    }

    /** O app encerrou a disputa, ou o pedido acabou: some da faixa de alerta. */
    @Transactional
    public void closed(OrderSource provider, String externalDisputeId) {
        disputes.findByProviderAndExternalDisputeId(provider, externalDisputeId)
                .ifPresent(dispute -> dispute.close(Instant.now(clock)));
    }

    @Transactional
    public void orderFinished(UUID orderId) {
        disputes.findAllByOrderIdAndStatus(orderId, MarketplaceDispute.Status.OPEN)
                .forEach(dispute -> dispute.close(Instant.now(clock)));
    }

    private static DisputeResponse toResponse(MarketplaceDispute dispute, OrderResponse order) {
        return new DisputeResponse(dispute.getId(), dispute.getOrderId(), order.number(), dispute.getProvider(),
                dispute.getKind(), dispute.getMessage(), dispute.getExpiresAt(), dispute.getStatus(), REJECT_REASONS);
    }
}
