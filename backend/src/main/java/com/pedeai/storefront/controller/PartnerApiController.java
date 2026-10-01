package com.pedeai.storefront.controller;

import com.pedeai.storefront.dto.PartnerOrderRequest;
import com.pedeai.storefront.dto.PartnerOrderResponse;
import com.pedeai.storefront.dto.StorefrontResponse;
import com.pedeai.storefront.service.ApiKeyService;
import com.pedeai.storefront.service.StorefrontService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * API de pedidos para sistemas de terceiros (sites, bots, outros cardápios), autenticada pela chave da loja no header
 * {@code X-Api-Key}. Sem login de usuário: liberada no SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1")
public class PartnerApiController {
    static final String KEY_HEADER = "X-Api-Key";

    private final ApiKeyService apiKeyService;
    private final StorefrontService storefrontService;

    public PartnerApiController(ApiKeyService apiKeyService, StorefrontService storefrontService) {
        this.apiKeyService = apiKeyService;
        this.storefrontService = storefrontService;
    }

    /** O cardápio da loja, com os ids que os pedidos usam, as áreas de entrega e as formas de pagamento. */
    @GetMapping("/menu")
    public StorefrontResponse menu(@RequestHeader(name = KEY_HEADER, required = false) String key) {
        return storefrontService.menu(apiKeyService.authenticate(key));
    }

    /** 201 com o pedido novo, ou 200 com o já criado quando o mesmo {@code externalId} chega de novo. */
    @PostMapping("/orders")
    public ResponseEntity<PartnerOrderResponse> placeOrder(
            @RequestHeader(name = KEY_HEADER, required = false) String key,
            @Valid @RequestBody PartnerOrderRequest request) {
        UUID storeId = apiKeyService.authenticate(key);
        StorefrontService.PartnerOrder placed = storefrontService.placePartnerOrder(storeId, request);
        if (!placed.created()) {
            return ResponseEntity.ok(placed.order());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/orders/" + placed.order().id())).body(placed.order());
    }

    @GetMapping("/orders/{id}")
    public PartnerOrderResponse order(@RequestHeader(name = KEY_HEADER, required = false) String key,
                                      @PathVariable UUID id) {
        return storefrontService.partnerOrder(apiKeyService.authenticate(key), id);
    }
}
