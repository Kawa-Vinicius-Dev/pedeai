package com.pedeai.printing.controller;

import com.pedeai.printing.dto.AgentConfigResponse;
import com.pedeai.printing.dto.AgentPairingRequest;
import com.pedeai.printing.dto.AgentPairingResponse;
import com.pedeai.printing.dto.AgentPrincipal;
import com.pedeai.printing.dto.AgentStatusRequest;
import com.pedeai.printing.service.AgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API usada pelo agente de impressão, autenticada pelo token de dispositivo (ver AgentSecurityConfig). */
@RestController
@RequestMapping("/api/agent")
public class AgentApiController {
    private final AgentService agentService;

    public AgentApiController(AgentService agentService) {
        this.agentService = agentService;
    }

    /** A única chamada sem token: troca o código de pareamento pelo token. */
    @PostMapping("/pairings")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentPairingResponse pair(@Valid @RequestBody AgentPairingRequest request, HttpServletRequest http) {
        return agentService.pair(request, http.getRemoteAddr());
    }

    @GetMapping("/config")
    public AgentConfigResponse config(@AuthenticationPrincipal AgentPrincipal agent) {
        return agentService.config(agent);
    }

    /** Heartbeat a cada 20 s. */
    @PutMapping("/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(@AuthenticationPrincipal AgentPrincipal agent, @Valid @RequestBody AgentStatusRequest request) {
        agentService.heartbeat(agent, request);
    }
}
