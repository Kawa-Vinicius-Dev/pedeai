package com.pedeai.printing.controller;

import com.pedeai.printing.config.AgentSecurityConfig;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.dto.AgentConfigResponse;
import com.pedeai.printing.dto.AgentPrincipal;
import com.pedeai.printing.dto.PairingCodeResponse;
import com.pedeai.printing.dto.PrintJobResponse;
import com.pedeai.printing.service.AgentService;
import com.pedeai.printing.service.PrintJobService;
import com.pedeai.printing.service.PrintQueueService;
import com.pedeai.printing.service.PrinterService;
import com.pedeai.printing.service.TicketService;
import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import com.pedeai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PrintAgentController.class, PrinterController.class, PrintJobController.class, TicketController.class,
        AgentApiController.class})
@Import({SecurityConfig.class, TimeConfig.class, AgentSecurityConfig.class})
class PrintingControllersTest {
    private static final UUID AGENT_ID = UUID.fromString("01a0d567-0000-7000-8000-0000000000a0");
    private static final UUID ORDER_ID = UUID.fromString("01a0d567-0000-7000-8000-0000000000a3");
    private static final UUID JOB_ID = UUID.fromString("01a0d567-0000-7000-8000-0000000000a4");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentService agentService;
    @MockitoBean
    private PrinterService printerService;
    @MockitoBean
    private PrintJobService printJobService;
    @MockitoBean
    private PrintQueueService printQueueService;
    @MockitoBean
    private TicketService ticketService;

    @Test
    void ownerGetsAPairingCodeAndTheCashierDoesNot() throws Exception {
        when(agentService.createPairingCode(STORE_ID)).thenReturn(new PairingCodeResponse("482913", NOW));

        mockMvc.perform(post("/api/print-agents/pairing-codes").with(as(Role.OWNER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("482913"));
        mockMvc.perform(post("/api/print-agents/pairing-codes").with(as(Role.CASHIER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void printerBodyIsValidatedAndOnlyManagersRegister() throws Exception {
        mockMvc.perform(post("/api/printers").with(as(Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"connectionType\":\"NETWORK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("Informe o nome da impressora."))
                .andExpect(jsonPath("$.fields.agentId").exists());
        mockMvc.perform(get("/api/printers").with(as(Role.CASHIER))).andExpect(status().isOk());
        mockMvc.perform(post("/api/printers").with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agentId":"%s","name":"Bar","connectionType":"NETWORK","host":"10.0.0.9","port":9100,
                                 "paperWidthMm":80,"columns":48,"codepage":"PC860","cutMode":"PARTIAL","active":true}"""
                                .formatted(AGENT_ID)))
                .andExpect(status().isForbidden());
        verify(printerService, never()).create(any(), any());
    }

    @Test
    void reprintAnswers201WithLocationAndPassesTheClickKey() throws Exception {
        when(printJobService.reprint(any(CurrentUser.class), eq(ORDER_ID), any(), eq("clique-1"))).thenReturn(
                new PrintJobResponse(JOB_ID, ORDER_ID, "Pedido 42 · Cozinha (reimpressão)", UUID.randomUUID(),
                        DocumentType.PRODUCTION_TICKET, null, PrintJob.Reason.REPRINT, PrintJob.Status.PENDING, 0,
                        null, "texto", NOW, null));

        mockMvc.perform(post("/api/orders/" + ORDER_ID + "/print-jobs").with(as(Role.CASHIER))
                        .header("Idempotency-Key", "clique-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"PRODUCTION_TICKET\",\"printerId\":\"%s\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/print-jobs/" + JOB_ID))
                .andExpect(jsonPath("$.reason").value("REPRINT"));
    }

    @Test
    void printJobPagesAreBounded() throws Exception {
        mockMvc.perform(get("/api/print-jobs").param("size", "500").with(as(Role.CASHIER)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/print-alerts").with(as(Role.WAITER))).andExpect(status().isForbidden());
    }

    @Test
    void agentApiNeedsTheDeviceToken() throws Exception {
        when(agentService.authenticate("token-bom")).thenReturn(Optional.of(new AgentPrincipal(AGENT_ID, STORE_ID)));
        when(agentService.authenticate("token-ruim")).thenReturn(Optional.empty());
        when(agentService.config(any())).thenReturn(new AgentConfigResponse(AGENT_ID, "Caixa", List.of()));

        mockMvc.perform(get("/api/agent/config")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/agent/config").header(HttpHeaders.AUTHORIZATION, "Bearer token-ruim"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/agent/config").with(as(Role.OWNER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agent/config").header(HttpHeaders.AUTHORIZATION, "Bearer token-bom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentName").value("Caixa"));
    }

    @Test
    void pairingIsPublicButValidatesTheCode() throws Exception {
        mockMvc.perform(post("/api/agent/pairings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"12ab\",\"name\":\"Caixa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.code").value("O código tem 6 dígitos."));
        verify(agentService, never()).pair(any(), anyString());
    }
}
