package com.pedeai.store.controller;

import com.pedeai.shared.security.CurrentUser;
import com.pedeai.store.dto.MeResponse;
import com.pedeai.store.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/me")
    public MeResponse me(CurrentUser user) {
        return userService.me(user.storeId(), user.userId());
    }
}
