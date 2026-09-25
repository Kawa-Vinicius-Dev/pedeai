package com.pedeai.order.controller;

import com.pedeai.order.dto.ChangeOrderStatusRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderStatusService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class OrderStatusController {
    private final OrderStatusService orderStatusService;

    public OrderStatusController(OrderStatusService orderStatusService) {
        this.orderStatusService = orderStatusService;
    }

    @PatchMapping("/api/orders/{id}/status")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public OrderResponse change(CurrentUser user, @PathVariable UUID id,
                                @Valid @RequestBody ChangeOrderStatusRequest request) {
        return orderStatusService.change(user, id, request);
    }
}
