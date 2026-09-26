package com.pedeai.printing.service;

import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.domain.ItemStatus;
import com.pedeai.order.dto.OrderItemResponse;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.payment.service.PaymentService;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Monta os documentos de um pedido. Hoje servem a impressão pelo navegador; o agente vai usar os mesmos. */
@Service
public class TicketService {
    static final String SECTOR_REQUIRED = "Escolha o setor do ticket de produção.";
    static final String KITCHEN_PRODUCTION_ONLY = "A cozinha só imprime tickets de produção.";

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final SectorService sectorService;
    private final StoreService storeService;
    private final Clock clock;

    public TicketService(OrderService orderService, PaymentService paymentService, SectorService sectorService,
                         StoreService storeService, Clock clock) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.sectorService = sectorService;
        this.storeService = storeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TicketResponse render(CurrentUser user, UUID orderId, DocumentType type, UUID sectorId, int columns) {
        OrderResponse order = orderService.get(user.storeId(), orderId);
        StoreResponse store = storeService.get(user.storeId());
        ZoneId zone = ZoneId.of(store.timezone());
        if (type == DocumentType.ORDER_TICKET) {
            if (user.role() == Role.KITCHEN) {
                throw new ForbiddenOperationException(KITCHEN_PRODUCTION_ONLY);
            }
            return TicketLayout.orderTicket(order, store.name(), paymentService.list(user.storeId(), orderId),
                    columns, zone);
        }
        if (sectorId == null) {
            throw new BusinessRuleException(SECTOR_REQUIRED);
        }
        String sectorName = sectorService.get(user.storeId(), sectorId).name();
        List<OrderItemResponse> items = order.items().stream()
                .filter(item -> item.status() == ItemStatus.ACTIVE && sectorId.equals(item.sectorId()))
                .toList();
        if (items.isEmpty()) {
            throw new BusinessRuleException("O pedido " + order.number() + " não tem itens para " + sectorName + ".");
        }
        return TicketLayout.production(order, sectorName, items, columns, zone, Instant.now(clock));
    }
}
