package com.pedeai.order.controller;

import com.pedeai.order.dto.LoyaltyStatusResponse;
import com.pedeai.order.service.LoyaltyService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class LoyaltyController {
    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    /** Quem lança o pedido vê se o cliente tem prêmio de fidelidade para usar. */
    @GetMapping("/api/customers/{customerId}/loyalty")
    @PreAuthorize(Permissions.TAKE_ORDERS)
    public LoyaltyStatusResponse status(CurrentUser user, @PathVariable UUID customerId) {
        return loyaltyService.status(user.storeId(), customerId);
    }
}
