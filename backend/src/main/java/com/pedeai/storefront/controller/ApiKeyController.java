package com.pedeai.storefront.controller;

import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.storefront.dto.ApiKeyRequest;
import com.pedeai.storefront.dto.ApiKeyResponse;
import com.pedeai.storefront.dto.CreatedApiKeyResponse;
import com.pedeai.storefront.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Chaves da API de pedidos da loja: criar, listar e revogar. */
@RestController
@RequestMapping("/api/api-keys")
@PreAuthorize(Permissions.MANAGE_SETTINGS)
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public List<ApiKeyResponse> list(CurrentUser user) {
        return apiKeyService.list(user.storeId());
    }

    @PostMapping
    public ResponseEntity<CreatedApiKeyResponse> create(CurrentUser user, @Valid @RequestBody ApiKeyRequest request) {
        CreatedApiKeyResponse created = apiKeyService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/api-keys/" + created.key().id())).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(CurrentUser user, @PathVariable UUID id) {
        apiKeyService.revoke(user.storeId(), id);
        return ResponseEntity.noContent().build();
    }
}
