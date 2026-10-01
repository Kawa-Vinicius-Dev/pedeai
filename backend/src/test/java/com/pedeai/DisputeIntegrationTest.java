package com.pedeai;

import com.jayway.jsonpath.JsonPath;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Disputa do iFood pelo simulador: o cliente pede o cancelamento, a loja vê com o prazo e responde. Aceitar cancela
 * quando o "iFood" confirmar; recusar pede um motivo; depois do prazo, só o app decide.
 */
@SpringBootTest(properties = "app.ifood.simulator=true")
@AutoConfigureMockMvc
class DisputeIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IntegrationScheduler scheduler;
    @Autowired
    private InboundService inbound;
    @Autowired
    private ObjectMapper json;

    @Test
    void customerCancellationRequestIsAnsweredByTheStore() throws Exception {
        String owner = register();
        String merchant = "merchant-" + UUID.randomUUID();
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"10","name":"X-Burger","priceCents":2990,"optionGroupIds":[],
                 "available":true,"active":true}""".formatted(category)).andExpect(status().isCreated());
        String connection = id(send(owner, post("/api/integrations"), """
                {"externalMerchantId":"%s","autoConfirm":true}""".formatted(merchant)));

        String first = simulatedOrder(owner, connection);
        String second = simulatedOrder(owner, connection);
        String third = simulatedOrder(owner, connection);
        dispute(merchant, first, "d-1", Instant.now().plusSeconds(300));
        dispute(merchant, second, "d-2", Instant.now().plusSeconds(300));
        dispute(merchant, third, "d-3", Instant.now().minusSeconds(5));
        dispute(merchant, first, "d-1", Instant.now().plusSeconds(300));
        scheduler.processInbox();

        String open = send(owner, get("/api/marketplace/disputes"), "")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].provider").value("IFOOD"))
                .andExpect(jsonPath("$[0].message").value("Demorou demais"))
                .andExpect(jsonPath("$[0].rejectReasons.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        // As três chegam no mesmo instante: a ordem da lista não diz qual é qual, o pedido diz.
        String firstOrder = orderId(owner, first);
        String secondOrder = orderId(owner, second);
        String firstDispute = disputeOf(open, firstOrder);
        String secondDispute = disputeOf(open, secondOrder);
        String thirdDispute = disputeOf(open, orderId(owner, third));

        // Aceitar: a resposta vai ao "iFood", que confirma o cancelamento.
        send(owner, post("/api/marketplace/disputes/" + firstDispute + "/answer"), "{\"accept\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        send(owner, post("/api/marketplace/disputes/" + firstDispute + "/answer"), "{\"accept\":true}")
                .andExpect(status().isUnprocessableContent());
        scheduler.sendOutbox();
        scheduler.processInbox();
        send(owner, get("/api/orders/" + firstOrder), "")
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("iFood: Cancelamento pedido pelo cliente"));
        send(owner, get("/api/orders/" + firstOrder + "/marketplace/sync"), "")
                .andExpect(jsonPath("$[?(@.action == 'ACCEPT_DISPUTE')].status").value("DONE"));

        // Recusar pede um dos motivos; o pedido continua.
        send(owner, post("/api/marketplace/disputes/" + secondDispute + "/answer"), "{\"accept\":false}")
                .andExpect(status().isUnprocessableContent());
        send(owner, post("/api/marketplace/disputes/" + secondDispute + "/answer"), """
                {"accept":false,"rejectCode":"DISH_ALREADY_DONE"}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        send(owner, get("/api/orders/" + secondOrder), "").andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Depois do prazo, quem decide é o app.
        send(owner, post("/api/marketplace/disputes/" + thirdDispute + "/answer"), "{\"accept\":true}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("O prazo para responder acabou: quem decide agora é o app."));
        send(owner, get("/api/marketplace/disputes"), "").andExpect(jsonPath("$.length()").value(1));
    }

    private static String disputeOf(String disputes, String orderId) {
        List<String> ids = JsonPath.read(disputes, "$[?(@.orderId == '" + orderId + "')].id");
        return ids.getFirst();
    }

    private void dispute(String merchant, String externalOrderId, String disputeId, Instant expiresAt) {
        inbound.record(OrderSource.IFOOD, json.valueToTree(Map.of("id", "hsd-" + disputeId, "code", "HSD",
                "fullCode", "HANDSHAKE_DISPUTE", "orderId", externalOrderId, "merchantId", merchant,
                "metadata", Map.of("disputeId", disputeId, "action", "CANCELLATION", "message", "Demorou demais",
                        "expiresAt", expiresAt.toString()))));
    }

    private String simulatedOrder(String owner, String connection) throws Exception {
        String body = send(owner, post("/api/integrations/" + connection + "/simulated-orders"), "")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        scheduler.processInbox();
        // O aceite automático vai ao "iFood" antes de tudo: as ações de um pedido saem em ordem.
        scheduler.sendOutbox();
        return JsonPath.read(body, "$.externalOrderId");
    }

    private String orderId(String owner, String externalOrderId) throws Exception {
        String orders = send(owner, get("/api/orders").param("size", "50"), "").andReturn().getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(orders, "$.content[*].id");
        for (String id : ids) {
            String order = send(owner, get("/api/orders/" + id), "").andReturn().getResponse().getContentAsString();
            if (externalOrderId.equals(JsonPath.read(order, "$.externalId"))) {
                return id;
            }
        }
        throw new AssertionError("pedido " + externalOrderId + " não encontrado");
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Disputa %s","ownerName":"Ana","email":"disputa-%s@example.com","password":"senha-forte-1"}
                        """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
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
