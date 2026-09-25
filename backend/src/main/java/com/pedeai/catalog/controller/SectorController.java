package com.pedeai.catalog.controller;

import com.pedeai.catalog.dto.SectorRequest;
import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.SectorService;
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
@RequestMapping("/api/sectors")
public class SectorController {
    private final SectorService sectorService;

    public SectorController(SectorService sectorService) {
        this.sectorService = sectorService;
    }

    @GetMapping
    public List<SectorResponse> list(CurrentUser user) {
        return sectorService.list(user.storeId());
    }

    @GetMapping("/{id}")
    public SectorResponse get(CurrentUser user, @PathVariable UUID id) {
        return sectorService.get(user.storeId(), id);
    }

    @PostMapping
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public ResponseEntity<SectorResponse> create(CurrentUser user, @Valid @RequestBody SectorRequest request) {
        SectorResponse created = sectorService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/sectors/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public SectorResponse update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody SectorRequest request) {
        return sectorService.update(user.storeId(), id, request);
    }
}
