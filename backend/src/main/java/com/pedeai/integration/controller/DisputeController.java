package com.pedeai.integration.controller;

import com.pedeai.integration.dto.DisputeAnswerRequest;
import com.pedeai.integration.dto.DisputeResponse;
import com.pedeai.integration.service.DisputeService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Pedidos de cancelamento feitos pelo cliente no app: caixa, gerente e dono respondem. */
@RestController
@RequestMapping("/api/marketplace/disputes")
@PreAuthorize(Permissions.TAKE_ORDERS)
public class DisputeController {
    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    public List<DisputeResponse> open(CurrentUser user) {
        return disputeService.open(user.storeId());
    }

    @PostMapping("/{id}/answer")
    public DisputeResponse answer(CurrentUser user, @PathVariable UUID id,
                                  @Valid @RequestBody DisputeAnswerRequest request) {
        return disputeService.answer(user, id, request);
    }
}
