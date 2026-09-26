package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import db.migration.V4__create_default_payment_methods;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de pronto da Etapa 2: um delivery com adicionais, taxa e troco percorre todos os status, e as telas
 * abertas recebem o aviso. Banco, segurança e tempo real de verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deliveryWithHalfAndHalfFeeAndChangeGoesThroughEveryStatus() throws Exception {
        String owner = register("ana");
        Menu menu = pizzeria(owner);
        send(owner, post("/api/delivery-zones"), """
                {"neighborhood":"Centro","feeCents":800,"active":true}""").andExpect(status().isCreated());
        List<String> methods = JsonPath.read(send(owner, get("/api/payment-methods"), "")
                .andExpect(jsonPath("$.length()").value(7))
                .andReturn().getResponse().getContentAsString(), "$[?(@.type == 'CASH')].id");
        String cash = methods.getFirst();

        // Pizza meio a meio pelo sabor mais caro (R$ 52,90) + 2 refrigerantes (R$ 14,00) + entrega (R$ 8,00)
        String body = send(owner, post("/api/orders"), """
                {"type":"DELIVERY","customer":{"name":"Maria","phone":"(11) 99999-0000"},
                 "deliveryAddress":{"street":"Rua das Flores","number":"120","complement":"Ap 32",
                                    "neighborhood":"Centro","city":"São Paulo","state":"SP"},
                 "items":[{"productId":"%s","quantity":1,"notes":"sem cebola","options":[
                            {"optionId":"%s","quantity":1},{"optionId":"%s","quantity":1}]},
                          {"productId":"%s","quantity":2,"options":[]}],
                 "notes":"Interfone quebrado","discountCents":0,"deliveryFeeCents":800,
                 "payments":[{"paymentMethodId":"%s","amountCents":7490,"changeForCents":10000,"paid":false}]}
                """.formatted(menu.pizza(), menu.calabresa(), menu.fourCheese(), menu.soda(), cash))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.subtotalCents").value(5290 + 1400))
                .andExpect(jsonPath("$.totalCents").value(7490))
                .andExpect(jsonPath("$.items[0].optionsPriceCents").value(5290))
                .andExpect(jsonPath("$.items[0].options.length()").value(2))
                .andExpect(jsonPath("$.items[0].sectorId").value(menu.kitchen()))
                .andExpect(jsonPath("$.customerPhone").value("+5511999990000"))
                .andExpect(jsonPath("$.deliveryAddress.neighborhood").value("Centro"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");

        send(owner, get("/api/orders/" + orderId + "/payments"), "")
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].changeCents").value(2510));
        send(owner, get("/api/customers").param("phone", "11999990000"), "")
                .andExpect(jsonPath("$.content[0].name").value("Maria"))
                .andExpect(jsonPath("$.content[0].addresses.length()").value(1));

        // Segundo pedido do dia ganha o número 2.
        send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","customer":{"name":"João"},"items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(menu.soda()))
                .andExpect(jsonPath("$.number").value(2));
        send(owner, get("/api/orders/active"), "").andExpect(jsonPath("$.length()").value(2));

        long version = ((Number) JsonPath.read(body, "$.version")).longValue();
        for (String next : List.of("IN_PREPARATION", "READY", "DISPATCHED", "COMPLETED")) {
            String changed = send(owner, patch("/api/orders/" + orderId + "/status"), """
                    {"status":"%s","version":%d}""".formatted(next, version))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next))
                    .andReturn().getResponse().getContentAsString();
            version = ((Number) JsonPath.read(changed, "$.version")).longValue();
        }
        // Tela desatualizada tentando mexer no pedido: 409.
        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CANCELLED","reason":"teste","version":0}""").andExpect(status().isConflict());

        send(owner, get("/api/orders/" + orderId + "/history"), "")
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].toStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[4].toStatus").value("COMPLETED"))
                .andExpect(jsonPath("$[4].actorName").value("Ana"));
        send(owner, get("/api/orders/active"), "").andExpect(jsonPath("$.length()").value(1));
        send(owner, get("/api/orders").param("status", "COMPLETED"), "")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].itemsSummary").value("1x Pizza Grande, 2x Refrigerante lata"));

        // O entregador voltou com o dinheiro.
        String paymentId = JsonPath.read(send(owner, get("/api/orders/" + orderId + "/payments"), "")
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        send(owner, patch("/api/orders/" + orderId + "/payments/" + paymentId), """
                {"status":"PAID"}""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void cashierCannotCancelWhatTheKitchenAlreadyStarted() throws Exception {
        String owner = register("bia");
        Menu menu = pizzeria(owner);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        send(owner, post("/api/users"), """
                {"name":"Caio","email":"caio-%s@example.com","password":"senha-do-caio","role":"CASHIER"}"""
                .formatted(suffix)).andExpect(status().isCreated());
        String cashier = login("caio-" + suffix + "@example.com", "senha-do-caio");
        String orderId = id(send(cashier, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(menu.soda())));
        send(cashier, patch("/api/orders/" + orderId + "/status"), """
                {"status":"IN_PREPARATION"}""").andExpect(status().isOk());

        send(cashier, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CANCELLED","reason":"Cliente desistiu"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Só gerente ou dono pode cancelar pedido que já começou a ser preparado."));
        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CANCELLED","reason":"Cliente desistiu"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").value("Cliente desistiu"));
    }

    @Test
    void anotherStoreCannotSeeOrTouchTheOrder() throws Exception {
        String storeA = register("carla");
        String storeB = register("dora");
        Menu menu = pizzeria(storeA);
        String orderId = id(send(storeA, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(menu.soda())));

        send(storeB, get("/api/orders/" + orderId), "").andExpect(status().isNotFound());
        send(storeB, get("/api/orders/" + orderId + "/payments"), "").andExpect(status().isNotFound());
        send(storeB, patch("/api/orders/" + orderId + "/status"), """
                {"status":"READY"}""").andExpect(status().isNotFound());
        send(storeB, get("/api/orders/active"), "").andExpect(jsonPath("$.length()").value(0));
        // O produto da loja A não pode entrar num pedido da loja B.
        send(storeB, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(menu.soda()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void openScreensHearAboutNewOrdersOfTheirStoreOnly() throws Exception {
        String storeA = register("elis");
        String storeB = register("fabi");
        Menu menu = pizzeria(storeA);
        MvcResult streamA = mockMvc.perform(get("/api/stream").header(HttpHeaders.AUTHORIZATION, storeA))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult streamB = mockMvc.perform(get("/api/stream").header(HttpHeaders.AUTHORIZATION, storeB))
                .andExpect(request().asyncStarted()).andReturn();

        String orderId = id(send(storeA, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(menu.soda())));
        send(storeA, patch("/api/orders/" + orderId + "/status"), """
                {"status":"READY"}""").andExpect(status().isOk());

        String eventsA = streamA.getResponse().getContentAsString();
        assertThat(eventsA).contains("event:order.created").contains(orderId).contains("event:order.status_changed")
                .contains("\"status\":\"READY\"");
        assertThat(streamB.getResponse().getContentAsString()).doesNotContain(orderId);
    }

    @Test
    void kitchenScreenShowsOnlyWhatEachSectorStillHasToPrepare() throws Exception {
        String owner = register("gabi");
        String other = register("hana");
        Menu menu = pizzeria(owner);
        String bar = id(send(owner, post("/api/sectors"), """
                {"name":"Bar","defaultSector":false,"active":true}"""));
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Cervejas","active":true}"""));
        String beer = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","sectorId":"%s","code":"950","name":"Cerveja","priceCents":1200,
                 "optionGroupIds":[],"available":true,"active":true}""".formatted(category, bar)));
        String orderId = id(send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[
                   {"optionId":"%s","quantity":1}]},{"productId":"%s","quantity":2,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}"""
                .formatted(menu.pizza(), menu.calabresa(), beer)));

        send(owner, get("/api/kitchen/orders"), "").andExpect(jsonPath("$[0].items.length()").value(2));
        send(owner, get("/api/kitchen/orders").param("sectorId", menu.kitchen()), "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].name").value("Pizza Grande"));
        send(owner, get("/api/kitchen/orders").param("sectorId", bar), "")
                .andExpect(jsonPath("$[0].items[0].name").value("Cerveja"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(2));
        send(other, get("/api/kitchen/orders"), "").andExpect(jsonPath("$.length()").value(0));

        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"READY"}""").andExpect(status().isOk());
        send(owner, get("/api/kitchen/orders"), "").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void storesCreatedBeforeStage2GetTheDefaultPaymentMethods() throws Exception {
        UUID oldStore = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO store (id, name, timezone, business_day_cutoff, service_fee_bp, auto_confirm_own_orders,
                                   start_preparation_on_confirm, created_at, updated_at, version)
                VALUES (?, 'Loja antiga', 'America/Sao_Paulo', '05:00:00', 1000, TRUE, FALSE, ?, ?, 0)""",
                oldStore, now, now);

        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V4__create_default_payment_methods().migrate(context);
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_method WHERE store_id = ?", Integer.class,
                oldStore)).isEqualTo(7);
    }

    private record Menu(String kitchen, String pizza, String calabresa, String fourCheese, String soda) {
    }

    private Menu pizzeria(String owner) throws Exception {
        String kitchen = id(send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}"""));
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Pizzas","active":true}"""));
        String flavorsBody = send(owner, post("/api/option-groups"), """
                {"name":"Sabores","minChoices":1,"maxChoices":2,"pricingRule":"MAX","active":true,"options":[
                  {"code":"101","name":"Calabresa","priceCents":4590,"available":true,"active":true},
                  {"code":"102","name":"Quatro queijos","priceCents":5290,"available":true,"active":true}]}""")
                .andReturn().getResponse().getContentAsString();
        List<String> flavorIds = JsonPath.read(flavorsBody, "$.options[*].id");
        String pizza = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"500","name":"Pizza Grande","priceCents":0,
                 "optionGroupIds":["%s"],"available":true,"active":true}"""
                .formatted(category, JsonPath.read(flavorsBody, "$.id"))));
        String soda = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"900","name":"Refrigerante lata","priceCents":700,
                 "optionGroupIds":[],"available":true,"active":true}""".formatted(category)));
        return new Menu(kitchen, pizza, flavorIds.get(0), flavorIds.get(1), soda);
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
