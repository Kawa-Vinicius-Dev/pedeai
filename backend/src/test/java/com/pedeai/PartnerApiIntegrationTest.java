package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de pedidos de ponta a ponta: a loja cria uma chave, o sistema de terceiros lê o cardápio, cria o pedido sem
 * duplicar no reenvio e acompanha o status; a chave só enxerga os pedidos dela e para de valer ao ser revogada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PartnerApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void partnerReadsTheMenuPlacesAnOrderOnceAndFollowsIt() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = register("Burger " + suffix, "burger-" + suffix);
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        String burger = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","name":"X-Burger","priceCents":3000,"optionGroupIds":[],"available":true,
                 "active":true}""".formatted(category)));
        send(owner, put("/api/store/menu-open"), "{\"open\":true}").andExpect(status().isOk());

        String created = send(owner, post("/api/api-keys"), "{\"name\":\"Site da loja\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key.keyPrefix").value(org.hamcrest.Matchers.startsWith("pk_")))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(created, "$.secret");
        String keyId = JsonPath.read(created, "$.key.id");
        send(owner, get("/api/api-keys"), "")
                .andExpect(jsonPath("$[0].name").value("Site da loja"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());

        mockMvc.perform(get("/api/v1/menu")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/menu").header("X-Api-Key", "pk_errada")).andExpect(status().isUnauthorized());
        String menu = mockMvc.perform(get("/api/v1/menu").header("X-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].id").value(burger))
                .andReturn().getResponse().getContentAsString();
        List<String> cash = JsonPath.read(menu, "$.paymentMethods[?(@.type == 'CASH')].id");
        String order = """
                {"externalId":"site-123","type":"TAKEOUT","customerName":"Carla","customerPhone":"11966660000",
                 "items":[{"productId":"%s","quantity":2,"options":[]}],"paymentMethodId":"%s"}"""
                .formatted(burger, cash.getFirst());

        String placed = mockMvc.perform(post("/api/v1/orders").header("X-Api-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.externalId").value("site-123"))
                .andExpect(jsonPath("$.totalCents").value(6000))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(placed, "$.id");

        // O site reenviou (timeout): o mesmo pedido volta, sem criar outro.
        mockMvc.perform(post("/api/v1/orders").header("X-Api-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
        send(owner, get("/api/orders/active"), "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("API"));

        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CONFIRMED","version":0}""").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("X-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // A chave não enxerga o pedido do balcão, nem a chave de outra loja enxerga este.
        String counter = id(send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(burger)));
        mockMvc.perform(get("/api/v1/orders/" + counter).header("X-Api-Key", key))
                .andExpect(status().isNotFound());
        String other = register("Outra " + suffix, "outra-api-" + suffix);
        String otherKey = JsonPath.read(send(other, post("/api/api-keys"), "{\"name\":\"Outro\"}")
                .andReturn().getResponse().getContentAsString(), "$.secret");
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("X-Api-Key", otherKey))
                .andExpect(status().isNotFound());

        send(owner, delete("/api/api-keys/" + keyId), "").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/menu").header("X-Api-Key", key)).andExpect(status().isUnauthorized());
        send(owner, get("/api/api-keys"), "").andExpect(jsonPath("$[0].revokedAt").isNotEmpty());
    }

    private String register(String storeName, String email) throws Exception {
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"%s","ownerName":"Ana","email":"%s@example.com","password":"senha-forte-1"}
                        """.formatted(storeName, email)))
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
