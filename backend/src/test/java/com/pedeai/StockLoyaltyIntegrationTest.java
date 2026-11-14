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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Estoque simples e fidelidade de ponta a ponta. */
@SpringBootTest
@AutoConfigureMockMvc
class StockLoyaltyIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void ordersConsumeStockAndPauseTheProductAtZero() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = register("Lanchonete " + suffix, "estoque-" + suffix);
        String other = register("Vizinha " + suffix, "vizinha-" + suffix);
        String category = catalog(owner);
        String soda = product(owner, category, "Refrigerante");
        String water = product(owner, category, "Água");

        send(owner, get("/api/products/" + soda), "").andExpect(jsonPath("$.stockQuantity").doesNotExist());
        send(owner, put("/api/products/" + soda + "/stock"), "{\"quantity\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(3))
                .andExpect(jsonPath("$.available").value(true));
        send(owner, put("/api/products/" + soda + "/stock"), "{\"quantity\":-1}").andExpect(status().isBadRequest());
        send(other, put("/api/products/" + soda + "/stock"), "{\"quantity\":9}").andExpect(status().isNotFound());

        takeout(owner, soda, 2, null, false).andExpect(status().isCreated());
        send(owner, get("/api/products/" + soda), "")
                .andExpect(jsonPath("$.stockQuantity").value(1))
                .andExpect(jsonPath("$.available").value(true));
        // Quem não controla estoque não muda.
        takeout(owner, water, 5, null, false).andExpect(status().isCreated());
        send(owner, get("/api/products/" + water), "")
                .andExpect(jsonPath("$.stockQuantity").doesNotExist())
                .andExpect(jsonPath("$.available").value(true));

        // O último pedido leva o que sobrou: o estoque não fica negativo e o produto pausa sozinho.
        takeout(owner, soda, 2, null, false).andExpect(status().isCreated());
        send(owner, get("/api/products/" + soda), "")
                .andExpect(jsonPath("$.stockQuantity").value(0))
                .andExpect(jsonPath("$.available").value(false));
        takeout(owner, soda, 1, null, false).andExpect(status().isUnprocessableContent());

        // Zerar na mão pausa; repor libera; nulo deixa de controlar sem mexer na disponibilidade.
        send(owner, put("/api/products/" + soda + "/stock"), "{\"quantity\":5}")
                .andExpect(jsonPath("$.available").value(true));
        send(owner, put("/api/products/" + soda + "/stock"), "{\"quantity\":0}")
                .andExpect(jsonPath("$.available").value(false));
        send(owner, put("/api/products/" + soda + "/stock"), "{\"quantity\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").doesNotExist())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void customerEarnsAndUsesTheLoyaltyReward() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = register("Pizzaria " + suffix, "fidelidade-" + suffix);
        String category = catalog(owner);
        String soda = product(owner, category, "Refrigerante");
        String phone = "(11) 96666-1234";

        send(owner, put("/api/store/loyalty"), "{\"orders\":2,\"discountCents\":0}")
                .andExpect(status().isUnprocessableContent());
        send(owner, put("/api/store/loyalty"), "{\"orders\":1,\"discountCents\":1000}")
                .andExpect(status().isBadRequest());
        send(owner, put("/api/store/loyalty"), "{\"orders\":2,\"discountCents\":1000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyaltyOrders").value(2))
                .andExpect(jsonPath("$.loyaltyDiscountCents").value(1000));

        String first = id(takeout(owner, soda, 1, phone, false).andExpect(status().isCreated()));
        String customer = JsonPath.read(send(owner, get("/api/customers?phone=11966661234"), "").andReturn()
                .getResponse().getContentAsString(), "$.content[0].id");
        // Pedido em aberto ainda não conta.
        send(owner, get("/api/customers/" + customer + "/loyalty"), "")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.completedOrders").value(0))
                .andExpect(jsonPath("$.ordersToNext").value(2));
        complete(owner, first);
        send(owner, get("/api/customers/" + customer + "/loyalty"), "")
                .andExpect(jsonPath("$.ordersToNext").value(1))
                .andExpect(jsonPath("$.rewardAvailable").value(false));
        // Sem prêmio, o caixa não consegue usar.
        takeout(owner, soda, 1, phone, true).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Este cliente não tem prêmio de fidelidade para usar agora."));

        complete(owner, id(takeout(owner, soda, 1, phone, false)));
        send(owner, get("/api/customers/" + customer + "/loyalty"), "")
                .andExpect(jsonPath("$.rewardAvailable").value(true))
                .andExpect(jsonPath("$.rewardCents").value(1000))
                .andExpect(jsonPath("$.ordersToNext").value(0));

        // 2 refrigerantes (14,00) com o prêmio de 10,00.
        String reward = id(takeout(owner, soda, 2, phone, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountCents").value(1000))
                .andExpect(jsonPath("$.totalCents").value(400))
                .andExpect(jsonPath("$.notes").value("Prêmio de fidelidade")));
        send(owner, get("/api/customers/" + customer + "/loyalty"), "")
                .andExpect(jsonPath("$.rewardAvailable").value(false));
        takeout(owner, soda, 1, phone, true).andExpect(status().isUnprocessableContent());

        // Cancelar o pedido do prêmio devolve o prêmio.
        cancel(owner, reward);
        send(owner, get("/api/customers/" + customer + "/loyalty"), "")
                .andExpect(jsonPath("$.rewardAvailable").value(true));

        // No cardápio digital o prêmio entra sozinho pelo telefone, e nunca passa do valor dos itens.
        String slug = JsonPath.read(send(owner, get("/api/store"), "").andReturn().getResponse()
                .getContentAsString(), "$.slug");
        send(owner, put("/api/store/menu-open"), "{\"open\":true}").andExpect(status().isOk());
        String menu = mockMvc.perform(get("/api/public/stores/" + slug))
                .andExpect(jsonPath("$.loyaltyOrders").value(2))
                .andExpect(jsonPath("$.loyaltyDiscountCents").value(1000))
                .andReturn().getResponse().getContentAsString();
        List<String> cash = JsonPath.read(menu, "$.paymentMethods[?(@.type == 'CASH')].id");
        String menuOrder = """
                {"type":"TAKEOUT","customerName":"Maria","customerPhone":"%s",
                 "items":[{"productId":"%s","quantity":1,"options":[]}],"paymentMethodId":"%s"}"""
                .formatted(phone, soda, cash.getFirst());
        mockMvc.perform(post("/api/public/stores/" + slug + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(menuOrder))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(0));
        // O prêmio já foi: o pedido seguinte sai pelo preço cheio, sem erro.
        mockMvc.perform(post("/api/public/stores/" + slug + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(menuOrder))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(700));

        // Programa desligado: ninguém tem prêmio.
        send(owner, put("/api/store/loyalty"), "{\"orders\":null,\"discountCents\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.loyaltyOrders").doesNotExist());
        send(owner, get("/api/customers/" + customer + "/loyalty"), "")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.rewardAvailable").value(false));
    }

    private String catalog(String owner) throws Exception {
        send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}""").andExpect(status().isCreated());
        return id(send(owner, post("/api/categories"), """
                {"name":"Bebidas","active":true}"""));
    }

    private String product(String owner, String category, String name) throws Exception {
        return id(send(owner, post("/api/products"), """
                {"categoryId":"%s","name":"%s","priceCents":700,"optionGroupIds":[],"available":true,
                 "active":true}""".formatted(category, name)));
    }

    private ResultActions takeout(String owner, String product, int quantity, String phone, boolean reward)
            throws Exception {
        String customer = phone == null ? "{\"name\":\"Ana\"}" : "{\"name\":\"Maria\",\"phone\":\"" + phone + "\"}";
        return send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","customer":%s,"items":[{"productId":"%s","quantity":%d,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[],"loyaltyReward":%s}"""
                .formatted(customer, product, quantity, reward));
    }

    private void complete(String owner, String order) throws Exception {
        send(owner, patch("/api/orders/" + order + "/status"),
                "{\"status\":\"COMPLETED\",\"version\":%d}".formatted(version(owner, order)))
                .andExpect(status().isOk());
    }

    private void cancel(String owner, String order) throws Exception {
        send(owner, patch("/api/orders/" + order + "/status"),
                "{\"status\":\"CANCELLED\",\"reason\":\"Cliente desistiu\",\"version\":%d}"
                        .formatted(version(owner, order)))
                .andExpect(status().isOk());
    }

    private int version(String owner, String order) throws Exception {
        return JsonPath.read(send(owner, get("/api/orders/" + order), "").andReturn().getResponse()
                .getContentAsString(), "$.version");
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
