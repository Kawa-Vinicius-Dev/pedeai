package com.pedeai.order.service;

import com.pedeai.order.domain.Actor;
import com.pedeai.order.domain.ActorType;
import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderSource;
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
import com.pedeai.shared.text.Texts;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Muda o status do pedido e grava a linha do tempo. As regras de quem pode o quê estão em
 * docs/02-arquitetura.md (papéis) e docs/01-fluxos.md (transições).
 */
@Service
public class OrderStatusService {
    static final String STALE = "O pedido mudou em outra tela. Atualize e tente de novo.";
    static final String REASON_REQUIRED = "Informe o motivo do cancelamento.";
    static final String KITCHEN_ONLY_PREPARATION = "A cozinha só marca o pedido como em preparo ou pronto.";
    static final String CANCEL_AFTER_PREPARATION = "Só gerente ou dono pode cancelar pedido que já começou a ser preparado.";
    static final String CANCEL_COMPLETED_OWN_ONLY = "Pedido de marketplace concluído não pode ser cancelado aqui.";
    static final String KITCHEN_CANNOT_CANCEL = "A cozinha não cancela pedidos. Peça ao caixa ou ao gerente.";
    static final String MARKETPLACE_CANCEL_BY_REQUEST =
            "Pedido de marketplace é cancelado pela plataforma: use \"Solicitar cancelamento\" e escolha o motivo.";

    private static final Set<OrderStatus> KITCHEN_TARGETS = EnumSet.of(OrderStatus.IN_PREPARATION, OrderStatus.READY);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrderStatusService(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                              ApplicationEventPublisher events, Clock clock) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse change(CurrentUser user, UUID orderId, ChangeOrderStatusRequest request) {
        Order order = orderRepository.findByIdAndStoreId(orderId, user.storeId())
                .orElseThrow(() -> new ResourceNotFoundException(OrderService.NOT_FOUND));
        OrderStatus target = request.status();
        // Aplicar o status que o pedido já tem não faz nada, nem acusa conflito: é o que a outra tela queria.
        if (order.getStatus() == target) {
            return OrderResponse.from(order);
        }
        if (request.version() != null && request.version() != order.getVersion()) {
            throw new ConflictException(STALE);
        }
        OrderStatus from = order.getStatus();
        Instant now = Instant.now(clock);
        String reason = null;
        if (target == OrderStatus.CANCELLED) {
            reason = Texts.trimToNull(request.reason());
            checkCanCancel(user, order, reason);
            order.cancel(reason, now);
        } else {
            if (user.role() == Role.KITCHEN && !KITCHEN_TARGETS.contains(target)) {
                throw new ForbiddenOperationException(KITCHEN_ONLY_PREPARATION);
            }
            order.advanceTo(target, now);
        }
        historyRepository.save(new OrderStatusHistory(order, from, target, Actor.user(user.userId(), user.name()),
                reason, now));
        orderRepository.flush();
        events.publishEvent(new OrderStatusChanged(order.getStoreId(), order.getId(), order.getNumber(), from, target,
                order.getVersion(), ActorType.USER));
        return OrderResponse.from(order);
    }

    /**
     * Status que veio da plataforma (evento do iFood). Idempotente: evento repetido, atrasado ou "para trás" não faz
     * nada, porque os eventos chegam repetidos e fora de ordem.
     *
     * @return {@code true} se o pedido mudou
     */
    @Transactional
    public boolean applyFromMarketplace(UUID storeId, UUID orderId, OrderStatus target, String reason) {
        Order order = orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(OrderService.NOT_FOUND));
        OrderStatus from = order.getStatus();
        Instant now = Instant.now(clock);
        boolean changed;
        if (target == OrderStatus.CANCELLED) {
            changed = order.cancel(reason == null ? "Cancelado pela plataforma" : reason, now);
        } else {
            boolean stale = from.isFinal() || target.ordinal() <= from.ordinal()
                    || (target == OrderStatus.DISPATCHED && order.getType() != OrderType.DELIVERY);
            changed = !stale && order.advanceTo(target, now);
        }
        if (!changed) {
            return false;
        }
        historyRepository.save(new OrderStatusHistory(order, from, target, Actor.marketplace(order.getSource()),
                target == OrderStatus.CANCELLED ? order.getCancelReason() : null, now));
        orderRepository.flush();
        events.publishEvent(new OrderStatusChanged(order.getStoreId(), order.getId(), order.getNumber(), from, target,
                order.getVersion(), ActorType.MARKETPLACE));
        return true;
    }

    private static void checkCanCancel(CurrentUser user, Order order, String reason) {
        if (reason == null) {
            throw new BusinessRuleException(REASON_REQUIRED);
        }
        if (order.getSource() != OrderSource.PEDEAI && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessRuleException(MARKETPLACE_CANCEL_BY_REQUEST);
        }
        boolean manager = user.role() == Role.OWNER || user.role() == Role.MANAGER;
        if (user.role() == Role.KITCHEN) {
            throw new ForbiddenOperationException(KITCHEN_CANNOT_CANCEL);
        }
        if (order.getStatus().isPreparationStarted() && !manager) {
            throw new ForbiddenOperationException(CANCEL_AFTER_PREPARATION);
        }
        if (order.getStatus() == OrderStatus.COMPLETED && order.getSource() != OrderSource.PEDEAI) {
            throw new BusinessRuleException(CANCEL_COMPLETED_OWN_ONLY);
        }
    }
}
