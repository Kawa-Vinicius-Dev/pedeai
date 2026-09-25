package com.pedeai.store.controller;

import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.store.dto.AuthResponse;
import com.pedeai.store.dto.RegisterStoreRequest;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.dto.UpdateStoreRequest;
import com.pedeai.store.service.AuthResult;
import com.pedeai.store.service.StoreRegistrationService;
import com.pedeai.store.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class StoreController {
    private final StoreRegistrationService registrationService;
    private final StoreService storeService;
    private final RefreshTokenCookies cookies;

    public StoreController(StoreRegistrationService registrationService, StoreService storeService,
                           RefreshTokenCookies cookies) {
        this.registrationService = registrationService;
        this.storeService = storeService;
        this.cookies = cookies;
    }

    /** Cadastro público: cria a loja com o dono e já devolve a sessão dele. */
    @PostMapping("/api/stores")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterStoreRequest request,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                                 String userAgent) {
        AuthResult result = registrationService.register(request, userAgent);
        return ResponseEntity.created(URI.create("/api/store"))
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()))
                .body(result.response());
    }

    @GetMapping("/api/store")
    public StoreResponse get(CurrentUser user) {
        return storeService.get(user.storeId());
    }

    @PatchMapping("/api/store")
    @PreAuthorize(Permissions.OWNER)
    public StoreResponse update(CurrentUser user, @Valid @RequestBody UpdateStoreRequest request) {
        return storeService.update(user.storeId(), request);
    }
}
