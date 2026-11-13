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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Entregadores de ponta a ponta: cadastro, quem leva cada pedido e o acerto do dia. */
@SpringBootTest
@AutoConfigureMockMvc
class CourierIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void assignsCouriersAndSettlesTheDay() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = register("Pizzaria " + suffix, "dono-" + suffix);
        String other = register("Outra " + suffix, "outra-" + suffix);
        send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}""").andExpect(status().isCreated());
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Bebidas","active":true}"""));
        String soda = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","name":"Refrigerante","priceCents":700,"optionGroupIds":[],"available":true,
                 "active":true}""".formatted(category)));

        String joao = id(send(owner, post("/api/couriers"), """
                {"name":" João ","phone":"(11) 97777-0000","active":true}""").andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("João")));
        String retired = id(send(owner, post("/api/couriers"), """
                {"name":"Pedro","phone":null,"active":false}"""));
        String foreign = id(send(other, post("/api/couriers"), """
                {"name":"De outra loja","phone":null,"active":true}"""));
        send(owner, get("/api/couriers"), "").andExpect(jsonPath("$.length()").value(2));

        String first = delivery(owner, soda, 800);
        String second = delivery(owner, soda, 500);
        String unassigned = delivery(owner, soda, 600);
        String takeout = id(send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","customer":{"name":"Ana"},"items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(soda)));

        send(owner, patch("/api/orders/" + first + "/courier"), "{\"courierId\":\"%s\"}".formatted(joao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierName").value("João"))
                // Escolher o entregador não conta como "o pedido mudou": a versão que a tela usa continua a mesma.
                .andExpect(jsonPath("$.version").value(0));
        send(owner, patch("/api/orders/" + second + "/courier"), "{\"courierId\":\"%s\"}".formatted(joao))
                .andExpect(status().isOk());
        send(owner, patch("/api/orders/" + unassigned + "/courier"), "{\"courierId\":\"%s\"}".formatted(joao))
                .andExpect(status().isOk());
        send(owner, patch("/api/orders/" + unassigned + "/courier"), "{\"courierId\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.courierName").doesNotExist());

        // Só delivery tem entregador; inativo e de outra loja não entram.
        send(owner, patch("/api/orders/" + takeout + "/courier"), "{\"courierId\":\"%s\"}".formatted(joao))
                .andExpect(status().isUnprocessableContent());
        send(owner, patch("/api/orders/" + first + "/courier"), "{\"courierId\":\"%s\"}".formatted(retired))
                .andExpect(status().isUnprocessableContent());
        send(owner, patch("/api/orders/" + first + "/courier"), "{\"courierId\":\"%s\"}".formatted(foreign))
                .andExpect(status().isNotFound());
        send(other, patch("/api/orders/" + first + "/courier"), "{\"courierId\":\"%s\"}".formatted(foreign))
                .andExpect(status().isNotFound());

        // Entrega cancelada não entra no acerto. Renomear o entregador não muda o que já foi atribuído.
        send(owner, patch("/api/orders/" + second + "/status"), """
                {"status":"CANCELLED","reason":"Cliente desistiu","version":0}""").andExpect(status().isOk());
        send(owner, put("/api/couriers/" + joao), """
                {"name":"João Silva","phone":null,"active":true}""").andExpect(status().isOk());

        String day = JsonPath.read(send(owner, get("/api/orders/" + first), "").andReturn().getResponse()
                .getContentAsString(), "$.businessDate");
        send(owner, get("/api/couriers/settlement?businessDate=" + day), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.courierId == '%s')].deliveries".formatted(joao)).value(1))
                .andExpect(jsonPath("$[?(@.courierId == '%s')].deliveryFeeCents".formatted(joao)).value(800))
                .andExpect(jsonPath("$[?(@.courierId == null)].deliveries").value(1))
                .andExpect(jsonPath("$[?(@.courierId == null)].deliveryFeeCents").value(600));
        send(other, get("/api/couriers/settlement?businessDate=" + day), "")
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String delivery(String owner, String product, int fee) throws Exception {
        return id(send(owner, post("/api/orders"), """
                {"type":"DELIVERY","customer":{"name":"Maria","phone":"(11) 99999-0000"},
                 "deliveryAddress":{"street":"Rua das Flores","number":"120","neighborhood":"Centro"},
                 "items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":%d,"payments":[]}""".formatted(product, fee))
                .andExpect(status().isCreated()));
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
