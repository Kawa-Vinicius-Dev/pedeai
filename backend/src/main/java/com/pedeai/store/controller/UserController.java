package com.pedeai.store.controller;

import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.store.dto.CreateUserRequest;
import com.pedeai.store.dto.UpdateUserRequest;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@PreAuthorize(Permissions.OWNER)
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list(CurrentUser user) {
        return userService.list(user.storeId());
    }

    @GetMapping("/{id}")
    public UserResponse get(CurrentUser user, @PathVariable UUID id) {
        return userService.get(user.storeId(), id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(CurrentUser user, @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public UserResponse update(CurrentUser user, @PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(user.storeId(), id, request);
    }
}
