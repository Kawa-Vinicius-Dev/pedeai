package com.pedeai.payment.controller;

import com.pedeai.payment.domain.CashSession;
import com.pedeai.payment.dto.CashSessionResponse;
import com.pedeai.payment.service.CashSessionService;
import com.pedeai.report.controller.ReportController;
import com.pedeai.report.service.ReportService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CashSessionController.class, ReportController.class})
@Import({SecurityConfig.class, TimeConfig.class})
class CashAndReportControllersTest {
    private static final UUID SESSION_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000030");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashSessionService cashSessionService;
    @MockitoBean
    private ReportService reportService;

    @Test
    void cashierOpensTheCashWithLocationAndNoOpenCashIs204() throws Exception {
        when(cashSessionService.open(any(CurrentUser.class), any())).thenReturn(new CashSessionResponse(SESSION_ID,
                CashSession.Status.OPEN, NOW, "Ana", 10_000, null, null, null, List.of(), List.of(), 10_000, null,
                null));
        when(cashSessionService.current(STORE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/cash-sessions").with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"openingAmountCents\":10000}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/cash-sessions/" + SESSION_ID));
        mockMvc.perform(get("/api/cash-sessions/current").with(as(Role.CASHIER)))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidMovementAndKitchenAreRefused() throws Exception {
        mockMvc.perform(post("/api/cash-sessions/" + SESSION_ID + "/movements").with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WITHDRAWAL\",\"amountCents\":0,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/cash-sessions").with(as(Role.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"openingAmountCents\":0}"))
                .andExpect(status().isForbidden());
        verify(cashSessionService, never()).addMovement(any(), any(), any());
        verify(cashSessionService, never()).open(any(), any());
    }

    @Test
    void reportsAreForManagersOnly() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard").with(as(Role.CASHIER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/revenue").param("from", "2026-09-01").with(as(Role.MANAGER)))
                .andExpect(status().isBadRequest());
        verify(reportService, never()).revenue(any(), any(LocalDate.class), any(LocalDate.class));
    }
}
