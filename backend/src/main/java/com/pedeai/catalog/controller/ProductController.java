package com.pedeai.catalog.controller;

import com.pedeai.catalog.dto.AvailabilityRequest;
import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.dto.ProductRequest;
import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list(CurrentUser user, @RequestParam(required = false) UUID categoryId) {
        return productService.list(user.storeId(), categoryId);
    }

    @GetMapping("/{id}")
    public ProductResponse get(CurrentUser user, @PathVariable UUID id) {
        return productService.get(user.storeId(), id);
    }

    @PostMapping
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public ResponseEntity<ProductResponse> create(CurrentUser user, @Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public ProductResponse update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(user.storeId(), id, request);
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize(Permissions.TOGGLE_AVAILABILITY)
    public ProductResponse changeAvailability(CurrentUser user, @PathVariable UUID id,
                                              @Valid @RequestBody AvailabilityRequest request) {
        return productService.changeAvailability(user.storeId(), id, request.available());
    }

    /** Calcula o preço de um item montado. Não grava nada, por isso responde 200 e não 201. */
    @PostMapping("/{id}/price-quotes")
    public PriceQuoteResponse quote(CurrentUser user, @PathVariable UUID id,
                                    @Valid @RequestBody PriceQuoteRequest request) {
        return productService.quote(user.storeId(), id, request);
    }
}
