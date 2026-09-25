package com.pedeai.catalog.controller;

import com.pedeai.catalog.dto.AvailabilityRequest;
import com.pedeai.catalog.dto.OptionGroupRequest;
import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.service.OptionGroupService;
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
@RequestMapping("/api/option-groups")
public class OptionGroupController {
    private final OptionGroupService optionGroupService;

    public OptionGroupController(OptionGroupService optionGroupService) {
        this.optionGroupService = optionGroupService;
    }

    @GetMapping
    public List<OptionGroupResponse> list(CurrentUser user) {
        return optionGroupService.list(user.storeId());
    }

    @GetMapping("/{id}")
    public OptionGroupResponse get(CurrentUser user, @PathVariable UUID id) {
        return optionGroupService.get(user.storeId(), id);
    }

    @PostMapping
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public ResponseEntity<OptionGroupResponse> create(CurrentUser user,
                                                      @Valid @RequestBody OptionGroupRequest request) {
        OptionGroupResponse created = optionGroupService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/option-groups/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.MANAGE_CATALOG)
    public OptionGroupResponse update(CurrentUser user, @PathVariable UUID id,
                                      @Valid @RequestBody OptionGroupRequest request) {
        return optionGroupService.update(user.storeId(), id, request);
    }

    @PutMapping("/{groupId}/options/{optionId}/availability")
    @PreAuthorize(Permissions.TOGGLE_AVAILABILITY)
    public OptionGroupResponse changeOptionAvailability(CurrentUser user, @PathVariable UUID groupId,
                                                        @PathVariable UUID optionId,
                                                        @Valid @RequestBody AvailabilityRequest request) {
        return optionGroupService.changeOptionAvailability(user.storeId(), groupId, optionId, request.available());
    }
}
