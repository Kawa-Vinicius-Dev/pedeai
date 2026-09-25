package com.pedeai.payment.controller;

import com.pedeai.payment.dto.PaymentMethodRequest;
import com.pedeai.payment.dto.PaymentMethodResponse;
import com.pedeai.payment.service.PaymentMethodService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {
    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    public List<PaymentMethodResponse> list(CurrentUser user) {
        return paymentMethodService.list(user.storeId());
    }

    @PostMapping
    @PreAuthorize(Permissions.MANAGE_SETTINGS)
    public ResponseEntity<PaymentMethodResponse> create(CurrentUser user,
                                                        @Valid @RequestBody PaymentMethodRequest request) {
        PaymentMethodResponse created = paymentMethodService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/payment-methods/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.MANAGE_SETTINGS)
    public PaymentMethodResponse update(CurrentUser user, @PathVariable UUID id,
                                        @Valid @RequestBody PaymentMethodRequest request) {
        return paymentMethodService.update(user.storeId(), id, request);
    }
}
