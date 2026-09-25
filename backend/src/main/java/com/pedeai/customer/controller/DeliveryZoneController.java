package com.pedeai.customer.controller;

import com.pedeai.customer.dto.DeliveryZoneRequest;
import com.pedeai.customer.dto.DeliveryZoneResponse;
import com.pedeai.customer.service.DeliveryZoneService;
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
@RequestMapping("/api/delivery-zones")
public class DeliveryZoneController {
    private final DeliveryZoneService deliveryZoneService;

    public DeliveryZoneController(DeliveryZoneService deliveryZoneService) {
        this.deliveryZoneService = deliveryZoneService;
    }

    @GetMapping
    public List<DeliveryZoneResponse> list(CurrentUser user) {
        return deliveryZoneService.list(user.storeId());
    }

    @PostMapping
    @PreAuthorize(Permissions.MANAGE_SETTINGS)
    public ResponseEntity<DeliveryZoneResponse> create(CurrentUser user,
                                                       @Valid @RequestBody DeliveryZoneRequest request) {
        DeliveryZoneResponse created = deliveryZoneService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/delivery-zones/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.MANAGE_SETTINGS)
    public DeliveryZoneResponse update(CurrentUser user, @PathVariable UUID id,
                                       @Valid @RequestBody DeliveryZoneRequest request) {
        return deliveryZoneService.update(user.storeId(), id, request);
    }
}
