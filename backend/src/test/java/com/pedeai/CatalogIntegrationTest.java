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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monta o cardápio de uma pizzaria pela API, com banco e segurança de verdade. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void pizzeriaMenuPricesHalfAndHalfByTheMostExpensiveFlavor() throws Exception {
        String owner = register();
        String kitchen = id(send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":false,"active":true}"""));
        String bar = id(send(owner, post("/api/sectors"), """
                {"name":"Bar","defaultSector":false,"active":true}"""));
        String pizzas = id(send(owner, post("/api/categories"), """
                {"name":"Pizzas","defaultSectorId":null,"active":true}"""));
        String drinks = id(send(owner, post("/api/categories"), """
                {"name":"Bebidas","defaultSectorId":"%s","active":true}""".formatted(bar)));
        String flavorsBody = send(owner, post("/api/option-groups"), """
                {"name":"Sabores","minChoices":1,"maxChoices":2,"pricingRule":"MAX","active":true,"options":[
                  {"code":"101","name":"Calabresa","priceCents":4590,"available":true,"active":true},
                  {"code":"102","name":"Quatro queijos","priceCents":5290,"available":true,"active":true}]}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String flavors = JsonPath.read(flavorsBody, "$.id");
        List<String> flavorIds = JsonPath.read(flavorsBody, "$.options[*].id");
        String crustBody = send(owner, post("/api/option-groups"), """
                {"name":"Borda","minChoices":0,"maxChoices":1,"pricingRule":"SUM","active":true,"options":[
                  {"code":null,"name":"Catupiry","priceCents":800,"available":true,"active":true}]}""")
                .andReturn().getResponse().getContentAsString();
        String crust = JsonPath.read(crustBody, "$.id");
        String catupiry = JsonPath.read(crustBody, "$.options[0].id");

        String pizza = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"500","name":"Pizza Grande","description":"8 fatias","priceCents":0,
                 "sectorId":null,"optionGroupIds":["%s","%s"],"available":true,"active":true}"""
                .formatted(pizzas, flavors, crust)));
        send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"900","name":"Refrigerante lata","description":null,"priceCents":700,
                 "sectorId":null,"optionGroupIds":[],"available":true,"active":true}""".formatted(drinks))
                .andExpect(jsonPath("$.effectiveSectorId").value(bar));

        mockMvc.perform(get("/api/products/{id}", pizza).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveSectorId").value(kitchen))
                .andExpect(jsonPath("$.optionGroupIds[0]").value(flavors))
                .andExpect(jsonPath("$.optionGroupIds[1]").value(crust));

        send(owner, post("/api/products/" + pizza + "/price-quotes"), """
                {"quantity":2,"options":[{"optionId":"%s","quantity":1},{"optionId":"%s","quantity":1},
                  {"optionId":"%s","quantity":1}]}""".formatted(flavorIds.get(0), flavorIds.get(1), catupiry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPriceCents").value(5290 + 800))
                .andExpect(jsonPath("$.totalCents").value((5290 + 800) * 2))
                .andExpect(jsonPath("$.sectorId").value(kitchen));

        // Acabou a calabresa: pausada, deixa de poder ser pedida.
        send(owner, put("/api/option-groups/" + flavors + "/options/" + flavorIds.get(0) + "/availability"), """
                {"available":false}""").andExpect(status().isOk());
        send(owner, post("/api/products/" + pizza + "/price-quotes"), """
                {"quantity":1,"options":[{"optionId":"%s","quantity":1}]}""".formatted(flavorIds.get(0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Opção indisponível no momento: Calabresa."));
    }

    @Test
    void anotherStoreCannotSeeOrUseThisMenu() throws Exception {
        String storeA = register();
        String storeB = register();
        String categoryOfA = id(send(storeA, post("/api/categories"), """
                {"name":"Lanches","defaultSectorId":null,"active":true}"""));
        String productOfA = id(send(storeA, post("/api/products"), """
                {"categoryId":"%s","code":null,"name":"X-Burger","description":null,"priceCents":2500,
                 "sectorId":null,"optionGroupIds":[],"available":true,"active":true}""".formatted(categoryOfA)));

        mockMvc.perform(get("/api/products/{id}", productOfA).header(HttpHeaders.AUTHORIZATION, storeB))
                .andExpect(status().isNotFound());
        send(storeB, put("/api/products/" + productOfA + "/availability"), """
                {"available":false}""").andExpect(status().isNotFound());
        send(storeB, post("/api/products"), """
                {"categoryId":"%s","code":null,"name":"Intruso","description":null,"priceCents":1,
                 "sectorId":null,"optionGroupIds":[],"available":true,"active":true}""".formatted(categoryOfA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Categoria inválida."));
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, storeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Loja %s","ownerName":"Ana","email":"ana-%s@example.com","password":"senha-forte-1"}
                        """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions send(String authorization,
                               org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
