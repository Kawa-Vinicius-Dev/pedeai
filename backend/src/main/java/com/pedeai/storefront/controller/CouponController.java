package com.pedeai.storefront.controller;

import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.storefront.dto.CouponRequest;
import com.pedeai.storefront.dto.CouponResponse;
import com.pedeai.storefront.service.CouponService;
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

/** Cupons de desconto do cardápio digital: criar, listar e editar (desativar em vez de apagar). */
@RestController
@RequestMapping("/api/coupons")
@PreAuthorize(Permissions.MANAGE_SETTINGS)
public class CouponController {
    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public List<CouponResponse> list(CurrentUser user) {
        return couponService.list(user.storeId());
    }

    @PostMapping
    public ResponseEntity<CouponResponse> create(CurrentUser user, @Valid @RequestBody CouponRequest request) {
        CouponResponse created = couponService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/coupons/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CouponResponse update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CouponRequest request) {
        return couponService.update(user.storeId(), id, request);
    }
}
