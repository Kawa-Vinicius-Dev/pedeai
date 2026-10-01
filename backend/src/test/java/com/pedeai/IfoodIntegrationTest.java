package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import com.pedeai.integration.domain.InboundEvent;
import com.pedeai.integration.repository.InboundEventRepository;
import com.pedeai.integration.service.InboundService;
import com.pedeai.integration.service.IntegrationScheduler;
import com.pedeai.order.domain.OrderSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pedido do iFood de ponta a ponta pelo simulador (sem credenciais): vínculo, inbox, importação, status de volta pelo
 * outbox e cancelamento pedido à plataforma. Banco e segurança de verdade.
 */
@SpringBootTest(properties = "app.ifood.simulator=true")
@AutoConfigureMockMvc
class IfoodIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IntegrationScheduler scheduler;
    @Autowired
    private InboundService inbound;
    @Autowired
    private InboundEventRepository events;
    @Autowired
    private ObjectMapper json;

    @Test
    void simulatedIfoodOrderGoesThroughTheWholeFlow() throws Exception {
        String owner = register("tais");
        String merchant = "merchant-" + UUID.randomUUID();
        String kitchen = id(send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}"""));
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        String burger = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"10","name":"X-Burger","priceCents":2990,"optionGroupIds":[],
                 "available":true,"active":true}""".formatted(category)));

        send(owner, get("/api/integrations/ifood/setup"), "")
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.simulator").value(true));
        String connection = id(send(owner, post("/api/integrations"), """
                {"externalMerchantId":"%s","autoConfirm":false}""".formatted(merchant))
                .andExpect(status().isCreated()));
        // O mesmo merchant não liga em outra loja.
        send(register("uma"), post("/api/integrations"), """
                {"externalMerchantId":"%s","autoConfirm":false}""".formatted(merchant))
                .andExpect(status().isConflict());

        send(owner, post("/api/integrations/" + connection + "/simulated-orders"), "")
                .andExpect(status().isAccepted());
        scheduler.processInbox();

        String active = send(owner, get("/api/orders/active"), "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("IFOOD"))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].externalDisplayId").exists())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(active, "$[0].id");
        send(owner, get("/api/orders/" + orderId), "")
                .andExpect(jsonPath("$.items[0].productId").value(burger))
                .andExpect(jsonPath("$.items[0].sectorId").value(kitchen))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.notes").value(containsString("Localizador iFood")));
        send(owner, get("/api/orders/" + orderId + "/payments"), "").andExpect(jsonPath("$.length()").value(1));

        // Aceite na loja: sai para o iFood pelo outbox.
        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CONFIRMED"}""").andExpect(status().isOk());
        scheduler.sendOutbox();
        send(owner, get("/api/orders/" + orderId + "/marketplace/sync"), "")
                .andExpect(jsonPath("$[0].action").value("CONFIRM"))
                .andExpect(jsonPath("$[0].status").value("DONE"));

        // Cancelar direto não pode: é pedido à plataforma, com um motivo dela.
        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CANCELLED","reason":"Sem entregador"}""").andExpect(status().isUnprocessableContent());
        send(owner, get("/api/orders/" + orderId + "/marketplace/cancellation-reasons"), "")
                .andExpect(jsonPath("$[?(@.code == '503')].description").value("Item indisponível"));
        send(owner, post("/api/orders/" + orderId + "/marketplace/cancellation"), """
                {"code":"503","description":"Item indisponível"}""").andExpect(status().isAccepted());
        send(owner, post("/api/orders/" + orderId + "/marketplace/cancellation"), """
                {"code":"503","description":"Item indisponível"}""").andExpect(status().isConflict());
        send(owner, get("/api/orders/" + orderId), "").andExpect(jsonPath("$.status").value("CONFIRMED"));

        // O "iFood" aceita: volta como evento de cancelado, e só então o pedido é cancelado aqui.
        scheduler.sendOutbox();
        scheduler.processInbox();
        send(owner, get("/api/orders/" + orderId), "")
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("iFood: Item indisponível"));
        send(owner, get("/api/orders/" + orderId + "/history"), "")
                .andExpect(jsonPath("$[?(@.toStatus == 'CANCELLED')].actorName").value("iFood"));
    }

    @Test
    void eventsAreRecordedOnceAndUnknownMerchantsAreIgnored() {
        String eventId = "evt-" + UUID.randomUUID();
        var event = json.valueToTree(Map.of("id", eventId, "code", "CFM", "orderId", "o-1",
                "merchantId", "merchant-desconhecido"));

        assertThat(inbound.record(OrderSource.IFOOD, event)).isTrue();
        assertThat(inbound.record(OrderSource.IFOOD, event)).isFalse();
        scheduler.processInbox();

        InboundEvent stored = events.findAll().stream()
                .filter(candidate -> candidate.getExternalEventId().equals(eventId)).findFirst().orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(InboundEvent.Status.IGNORED);
        assertThat(stored.getLastError()).contains("sem vínculo");
    }

    @Test
    void onlyManagersLinkAndTheSimulatorNeedsALinkOfTheStore() throws Exception {
        String owner = register("vera");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        send(owner, post("/api/users"), """
                {"name":"Caio","email":"caio-%s@example.com","password":"senha-do-caio","role":"CASHIER"}"""
                .formatted(suffix)).andExpect(status().isCreated());
        String cashier = login("caio-" + suffix + "@example.com", "senha-do-caio");

        send(cashier, get("/api/integrations"), "").andExpect(status().isForbidden());
        send(owner, post("/api/integrations/" + UUID.randomUUID() + "/simulated-orders"), "")
                .andExpect(status().isNotFound());
        send(owner, get("/api/integrations/ifood/merchants"), "").andExpect(status().isUnprocessableContent());
    }

    private String register(String name) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Loja %s","ownerName":"Ana","email":"%s-%s@example.com","password":"senha-forte-1"}
                        """.formatted(suffix, name, suffix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions send(String authorization, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, authorization);
        if (!body.isEmpty()) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
