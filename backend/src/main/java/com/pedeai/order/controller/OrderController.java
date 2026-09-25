package com.pedeai.order.controller;

import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.CreateOrderRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.dto.OrderStatusHistoryResponse;
import com.pedeai.order.dto.OrderSummaryResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize(Permissions.TAKE_ORDERS)
    public ResponseEntity<OrderResponse> create(CurrentUser user, @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(user, request);
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    /** Quadro de pedidos: tudo o que ainda não foi concluído nem cancelado. */
    @GetMapping("/active")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public List<OrderSummaryResponse> active(CurrentUser user) {
        return orderService.listActive(user.storeId());
    }

    /** Histórico com filtros. {@code q}: número do pedido, ou parte do nome ou do telefone do cliente. */
    @GetMapping
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public PageResponse<OrderSummaryResponse> search(
            CurrentUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderType type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.search(user.storeId(), businessDate, status, type, q, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public OrderResponse get(CurrentUser user, @PathVariable UUID id) {
        return orderService.get(user.storeId(), id);
    }

    /** Linha do tempo: cada mudança de status, com quem mudou, quando e o motivo. */
    @GetMapping("/{id}/history")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public List<OrderStatusHistoryResponse> history(CurrentUser user, @PathVariable UUID id) {
        return orderService.history(user.storeId(), id);
    }
}
