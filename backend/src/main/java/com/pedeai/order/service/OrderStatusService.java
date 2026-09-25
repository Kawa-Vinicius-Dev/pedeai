package com.pedeai.order.service;

import com.pedeai.order.domain.Actor;
import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderStatusHistory;
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
                order.getVersion()));
        return OrderResponse.from(order);
    }

    private static void checkCanCancel(CurrentUser user, Order order, String reason) {
        if (reason == null) {
            throw new BusinessRuleException(REASON_REQUIRED);
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
