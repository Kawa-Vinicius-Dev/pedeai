package com.pedeai.printing.controller;

import com.pedeai.printing.dto.PairingCodeResponse;
import com.pedeai.printing.dto.PrintAgentResponse;
import com.pedeai.printing.service.AgentService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Computadores de impressão da loja, na tela de configurações. */
@RestController
@RequestMapping("/api/print-agents")
@PreAuthorize(Permissions.MANAGE_PRINTING)
public class PrintAgentController {
    private final AgentService agentService;

    public PrintAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public List<PrintAgentResponse> list(CurrentUser user) {
        return agentService.list(user.storeId());
    }

    /** "Adicionar computador de impressão": código de 6 dígitos, 10 minutos, uso único. */
    @PostMapping("/pairing-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public PairingCodeResponse createPairingCode(CurrentUser user) {
        return agentService.createPairingCode(user.storeId());
    }

    /** Revoga o token do computador. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(CurrentUser user, @PathVariable UUID id) {
        agentService.revoke(user.storeId(), id);
    }
}
