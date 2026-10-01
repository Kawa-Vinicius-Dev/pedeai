package com.pedeai.integration.controller;

import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.dto.ConnectionResponse;
import com.pedeai.integration.dto.OutboundActionResponse;
import com.pedeai.integration.service.ConnectionService;
import com.pedeai.integration.service.IfoodCatalogImportService;
import com.pedeai.integration.service.MarketplaceOrderService;
import com.pedeai.integration.service.SimulatorService;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.config.TimeConfig;
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

import java.util.UUID;

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({IntegrationController.class, MarketplaceOrderController.class})
@Import({SecurityConfig.class, TimeConfig.class})
class IntegrationControllersTest {
    private static final UUID CONNECTION = UUID.fromString("01a0d567-0000-7000-8000-0000000000b1");
    private static final UUID ORDER = UUID.fromString("01a0d567-0000-7000-8000-0000000000b2");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectionService connectionService;
    @MockitoBean
    private SimulatorService simulatorService;
    @MockitoBean
    private IfoodCatalogImportService ifoodCatalogImportService;
    @MockitoBean
    private MarketplaceOrderService marketplaceOrderService;

    @Test
    void managerLinksTheStoreAndGetsTheLocation() throws Exception {
        when(connectionService.connect(eq(STORE_ID), any())).thenReturn(new ConnectionResponse(CONNECTION,
                OrderSource.IFOOD, "m-1", "Lanchonete", MarketplaceConnection.Status.ACTIVE, false, null, null, 0));

        mockMvc.perform(post("/api/integrations").with(as(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalMerchantId\":\"m-1\",\"autoConfirm\":false}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/integrations/" + CONNECTION))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void linkIsValidatedAndIsOnlyForManagers() throws Exception {
        mockMvc.perform(post("/api/integrations").with(as(Role.OWNER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalMerchantId\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.externalMerchantId").value("Informe o id da loja na plataforma (merchant)."));
        mockMvc.perform(get("/api/integrations").with(as(Role.CASHIER))).andExpect(status().isForbidden());
        verify(connectionService, never()).connect(any(), any());
    }

    @Test
    void cancellationRequestIsAcceptedForLaterAndTheKitchenCannotAskForIt() throws Exception {
        when(marketplaceOrderService.requestCancellation(any(com.pedeai.shared.security.CurrentUser.class), eq(ORDER),
                any())).thenReturn(
                new OutboundActionResponse(UUID.randomUUID(), OutboundAction.Action.REQUEST_CANCELLATION,
                        OutboundAction.Status.PENDING, 0, null, NOW));
        String body = "{\"code\":\"503\",\"description\":\"Item indisponível\"}";

        mockMvc.perform(post("/api/orders/" + ORDER + "/marketplace/cancellation").with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.action").value("REQUEST_CANCELLATION"));
        mockMvc.perform(post("/api/orders/" + ORDER + "/marketplace/cancellation").with(as(Role.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
