package com.pedeai.integration.controller;

import com.pedeai.integration.dto.CancellationReasonResponse;
import com.pedeai.integration.dto.MarketplaceCancellationRequest;
import com.pedeai.integration.dto.OutboundActionResponse;
import com.pedeai.integration.service.MarketplaceOrderService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Pedido de marketplace na tela: sincronização com a plataforma e pedido de cancelamento. */
@RestController
public class MarketplaceOrderController {
    private final MarketplaceOrderService service;

    public MarketplaceOrderController(MarketplaceOrderService service) {
        this.service = service;
    }

    /** O que foi mandado para a plataforma e o que falhou. */
    @GetMapping("/api/orders/{orderId}/marketplace/sync")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public List<OutboundActionResponse> sync(CurrentUser user, @PathVariable UUID orderId) {
        return service.sync(user.storeId(), orderId);
    }

    /** Motivos aceitos pela plataforma para este pedido, agora. */
    @GetMapping("/api/orders/{orderId}/marketplace/cancellation-reasons")
    @PreAuthorize(Permissions.TAKE_ORDERS)
    public List<CancellationReasonResponse> cancellationReasons(CurrentUser user, @PathVariable UUID orderId) {
        return service.cancellationReasons(user.storeId(), orderId);
    }

    /** Aceito para envio: o pedido só é cancelado quando a plataforma confirmar. */
    @PostMapping("/api/orders/{orderId}/marketplace/cancellation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Permissions.TAKE_ORDERS)
    public OutboundActionResponse requestCancellation(CurrentUser user, @PathVariable UUID orderId,
                                                      @Valid @RequestBody MarketplaceCancellationRequest request) {
        return service.requestCancellation(user, orderId, request);
    }

    @PostMapping("/api/marketplace-actions/{id}/retry")
    @PreAuthorize(Permissions.TAKE_ORDERS)
    public OutboundActionResponse retry(CurrentUser user, @PathVariable UUID id) {
        return service.retry(user.storeId(), id);
    }
}
