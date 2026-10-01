package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import com.pedeai.integration.service.IntegrationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 99Food pelo padrão Open Delivery, com o simulador no lugar do app: o pedido entra pelo inbox, a loja aceita (o
 * aceite sai pelo outbox) e o cancelamento é pedido ao app com um motivo da especificação.
 */
@SpringBootTest(properties = "app.opendelivery.simulator=true")
@AutoConfigureMockMvc
class OpenDeliveryIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IntegrationScheduler scheduler;

    @Test
    void simulatedNinetyNineFoodOrderGoesThroughTheWholeFlow() throws Exception {
        String owner = register();
        send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}""").andExpect(status().isCreated());
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        String burger = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"10","name":"X-Burger","priceCents":2990,"optionGroupIds":[],
                 "available":true,"active":true}""".formatted(category)));

        send(owner, get("/api/integrations/platforms"), "")
                .andExpect(jsonPath("$[?(@.provider == 'NINETY_NINE_FOOD')].simulator").value(true))
                .andExpect(jsonPath("$[?(@.provider == 'IFOOD')].simulator").value(false));
        String connection = id(send(owner, post("/api/integrations"), """
                {"provider":"NINETY_NINE_FOOD","externalMerchantId":"99-%s","autoConfirm":false}"""
                .formatted(UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("NINETY_NINE_FOOD"))
                .andExpect(jsonPath("$.merchantName").value("99Food (simulado)")));
        // O iFood não está ligado neste servidor: não dá para vincular.
        send(owner, post("/api/integrations"), """
                {"provider":"IFOOD","externalMerchantId":"if-1","autoConfirm":false}""")
                .andExpect(status().isUnprocessableContent());

        send(owner, post("/api/integrations/" + connection + "/simulated-orders"), "")
                .andExpect(status().isAccepted());
        scheduler.processInbox();

        String active = send(owner, get("/api/orders/active"), "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("NINETY_NINE_FOOD"))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(active, "$[0].id");
        send(owner, get("/api/orders/" + orderId), "")
                .andExpect(jsonPath("$.items[0].productId").value(burger))
                .andExpect(jsonPath("$.deliveryFeeCents").value(700))
                .andExpect(jsonPath("$.deliveryAddress.neighborhood").value("Centro"));
        send(owner, get("/api/orders/" + orderId + "/payments"), "").andExpect(jsonPath("$.length()").value(1));

        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CONFIRMED"}""").andExpect(status().isOk());
        scheduler.sendOutbox();
        send(owner, get("/api/orders/" + orderId + "/marketplace/sync"), "")
                .andExpect(jsonPath("$[0].action").value("CONFIRM"))
                .andExpect(jsonPath("$[0].status").value("DONE"));

        send(owner, get("/api/orders/" + orderId + "/marketplace/cancellation-reasons"), "")
                .andExpect(jsonPath("$[?(@.code == 'UNAVAILABLE_ITEM')].description").value("Item indisponível"));
        send(owner, post("/api/orders/" + orderId + "/marketplace/cancellation"), """
                {"code":"UNAVAILABLE_ITEM","description":"Item indisponível"}""").andExpect(status().isAccepted());
        scheduler.sendOutbox();
        scheduler.processInbox();
        send(owner, get("/api/orders/" + orderId), "")
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("99Food: Item indisponível"));
        send(owner, get("/api/orders/" + orderId + "/history"), "")
                .andExpect(jsonPath("$[?(@.toStatus == 'CANCELLED')].actorName").value("99Food"));
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"OD %s","ownerName":"Ana","email":"od-%s@example.com","password":"senha-forte-1"}
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
