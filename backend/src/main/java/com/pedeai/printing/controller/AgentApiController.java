package com.pedeai.printing.controller;

import com.pedeai.printing.dto.AgentConfigResponse;
import com.pedeai.printing.dto.AgentJobResponse;
import com.pedeai.printing.dto.AgentJobUpdateRequest;
import com.pedeai.printing.dto.AgentPairingRequest;
import com.pedeai.printing.dto.AgentPairingResponse;
import com.pedeai.printing.dto.AgentPrincipal;
import com.pedeai.printing.dto.AgentStatusRequest;
import com.pedeai.printing.service.AgentService;
import com.pedeai.printing.service.PrintQueueService;
import com.pedeai.shared.web.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** API usada pelo agente de impressão, autenticada pelo token de dispositivo (ver AgentSecurityConfig). */
@RestController
@RequestMapping("/api/agent")
public class AgentApiController {
    private final AgentService agentService;
    private final PrintQueueService printQueueService;

    public AgentApiController(AgentService agentService, PrintQueueService printQueueService) {
        this.agentService = agentService;
        this.printQueueService = printQueueService;
    }

    /** A única chamada sem token: troca o código de pareamento pelo token. */
    @PostMapping("/pairings")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentPairingResponse pair(@Valid @RequestBody AgentPairingRequest request, HttpServletRequest http) {
        return agentService.pair(request, ClientAddress.of(http));
    }

    @GetMapping("/config")
    public AgentConfigResponse config(@AuthenticationPrincipal AgentPrincipal agent) {
        return agentService.config(agent);
    }

    /** Pendentes das impressoras deste computador, com os bytes ESC/POS. */
    @GetMapping("/jobs")
    public List<AgentJobResponse> jobs(@AuthenticationPrincipal AgentPrincipal agent) {
        return printQueueService.pendingFor(agent);
    }

    @PatchMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateJob(@AuthenticationPrincipal AgentPrincipal agent, @PathVariable UUID id,
                          @Valid @RequestBody AgentJobUpdateRequest request) {
        printQueueService.update(agent, id, request);
    }

    /** Heartbeat a cada 20 s. */
    @PutMapping("/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(@AuthenticationPrincipal AgentPrincipal agent, @Valid @RequestBody AgentStatusRequest request) {
        agentService.heartbeat(agent, request);
    }
}
