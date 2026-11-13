package com.pedeai.order.controller;

import com.pedeai.order.dto.AssignCourierRequest;
import com.pedeai.order.dto.CourierRequest;
import com.pedeai.order.dto.CourierResponse;
import com.pedeai.order.dto.CourierSettlementResponse;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.CourierService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Entregadores: quem lança pedido vê a lista e escolhe quem leva; gerente e dono cadastram e veem o acerto. */
@RestController
public class CourierController {
    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @GetMapping("/api/couriers")
    @PreAuthorize(Permissions.TAKE_ORDERS)
    public List<CourierResponse> list(CurrentUser user) {
        return courierService.list(user.storeId());
    }

    @PostMapping("/api/couriers")
    @PreAuthorize(Permissions.MANAGE_SETTINGS)
    public ResponseEntity<CourierResponse> create(CurrentUser user, @Valid @RequestBody CourierRequest request) {
        CourierResponse created = courierService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/couriers/" + created.id())).body(created);
    }

    @PutMapping("/api/couriers/{id}")
    @PreAuthorize(Permissions.MANAGE_SETTINGS)
    public CourierResponse update(CurrentUser user, @PathVariable UUID id,
                                  @Valid @RequestBody CourierRequest request) {
        return courierService.update(user.storeId(), id, request);
    }

    @GetMapping("/api/couriers/settlement")
    @PreAuthorize(Permissions.MANAGE_SETTINGS)
    public List<CourierSettlementResponse> settlement(
            CurrentUser user, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return courierService.settlement(user.storeId(), businessDate);
    }

    @PatchMapping("/api/orders/{id}/courier")
    @PreAuthorize(Permissions.TAKE_ORDERS)
    public OrderResponse assign(CurrentUser user, @PathVariable UUID id,
                                @Valid @RequestBody AssignCourierRequest request) {
        return courierService.assign(user.storeId(), id, request.courierId());
    }
}
