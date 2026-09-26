package com.pedeai.kitchen.controller;

import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Tela da cozinha. As ações (iniciar e pronto) usam a mesma rota de status do quadro de pedidos. */
@RestController
@RequestMapping("/api/kitchen")
public class KitchenController {
    private final OrderService orderService;

    public KitchenController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Confirmados e em preparo. Sem {@code sectorId}, todos os setores. */
    @GetMapping("/orders")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public List<OrderResponse> orders(CurrentUser user, @RequestParam(required = false) UUID sectorId) {
        return orderService.listForKitchen(user.storeId(), sectorId);
    }
}
