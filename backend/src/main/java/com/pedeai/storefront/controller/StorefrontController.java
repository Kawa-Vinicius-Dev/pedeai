package com.pedeai.storefront.controller;

import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.shared.web.ClientAddress;
import com.pedeai.storefront.dto.MenuOrderRequest;
import com.pedeai.storefront.dto.MenuOrderResponse;
import com.pedeai.storefront.dto.OrderTrackingResponse;
import com.pedeai.storefront.dto.StorefrontResponse;
import com.pedeai.storefront.service.StorefrontService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Cardápio digital: público, sem login (liberado no SecurityConfig). */
@RestController
@RequestMapping("/api/public")
public class StorefrontController {
    private final StorefrontService storefrontService;

    public StorefrontController(StorefrontService storefrontService) {
        this.storefrontService = storefrontService;
    }

    @GetMapping("/stores/{slug}")
    public StorefrontResponse menu(@PathVariable @Size(max = 60) String slug) {
        return storefrontService.menu(slug);
    }

    @PostMapping("/stores/{slug}/products/{productId}/price-quotes")
    public PriceQuoteResponse quote(@PathVariable @Size(max = 60) String slug, @PathVariable UUID productId,
                                    @Valid @RequestBody PriceQuoteRequest request) {
        return storefrontService.quote(slug, productId, request);
    }

    @PostMapping("/stores/{slug}/orders")
    public ResponseEntity<MenuOrderResponse> placeOrder(@PathVariable @Size(max = 60) String slug,
                                                        @Valid @RequestBody MenuOrderRequest request,
                                                        HttpServletRequest http) {
        MenuOrderResponse placed = storefrontService.placeOrder(slug, request, ClientAddress.of(http));
        return ResponseEntity.created(URI.create("/api/public/orders/" + placed.trackingCode())).body(placed);
    }

    @GetMapping("/orders/{trackingCode}")
    public OrderTrackingResponse track(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{10,40}") String trackingCode) {
        return storefrontService.track(trackingCode);
    }
}
