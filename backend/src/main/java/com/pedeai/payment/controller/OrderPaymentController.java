package com.pedeai.payment.controller;

import com.pedeai.payment.dto.PaymentRequest;
import com.pedeai.payment.dto.PaymentResponse;
import com.pedeai.payment.dto.PaymentStatusRequest;
import com.pedeai.payment.service.PaymentService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders/{orderId}/payments")
@PreAuthorize(Permissions.TAKE_ORDERS)
public class OrderPaymentController {
    private final PaymentService paymentService;

    public OrderPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentResponse> list(CurrentUser user, @PathVariable UUID orderId) {
        return paymentService.list(user.storeId(), orderId);
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> register(CurrentUser user, @PathVariable UUID orderId,
                                                    @Valid @RequestBody PaymentRequest request) {
        PaymentResponse created = paymentService.register(user, orderId, request);
        return ResponseEntity.created(URI.create("/api/orders/" + orderId + "/payments/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{paymentId}")
    public PaymentResponse changeStatus(CurrentUser user, @PathVariable UUID orderId, @PathVariable UUID paymentId,
                                        @Valid @RequestBody PaymentStatusRequest request) {
        return paymentService.changeStatus(user, orderId, paymentId, request);
    }
}
