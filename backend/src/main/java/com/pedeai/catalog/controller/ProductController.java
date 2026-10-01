package com.pedeai.catalog.controller;

import com.pedeai.catalog.dto.AvailabilityRequest;
import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.dto.ProductRequest;
import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.service.ProductImageService;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final ProductImageService imageService;

    public ProductController(ProductService productService, ProductImageService imageService) {
        this.productService = productService;
        this.imageService = imageService;
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

    /** Foto do produto (JPEG ou PNG, até 2 MB), no cardápio digital e no iFood. */
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public ProductResponse uploadImage(CurrentUser user, @PathVariable UUID id, @RequestParam("file") MultipartFile file)
            throws IOException {
        return imageService.upload(user.storeId(), id, file.getBytes());
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public ProductResponse removeImage(CurrentUser user, @PathVariable UUID id) {
        return imageService.remove(user.storeId(), id);
    }

    /** Calcula o preço de um item montado. Não grava nada, por isso responde 200 e não 201. */
    @PostMapping("/{id}/price-quotes")
    public PriceQuoteResponse quote(CurrentUser user, @PathVariable UUID id,
                                    @Valid @RequestBody PriceQuoteRequest request) {
        return productService.quote(user.storeId(), id, request);
    }
}
