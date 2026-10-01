package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de pronto da Etapa 7: o fechamento do caixa bate com os pagamentos do dia, e os números do dashboard e do
 * faturamento conferem com uma consulta direta no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CashAndReportsIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cashCloseMatchesThePaymentsAndReportsMatchTheDatabase() throws Exception {
        String owner = register();
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}""").andExpect(status().isCreated());
        String burger = product(owner, category, "X-Burger", 3000);
        String soda = product(owner, category, "Refrigerante", 700);
        send(owner, post("/api/delivery-zones"), """
                {"neighborhood":"Centro","feeCents":500,"active":true}""").andExpect(status().isCreated());
        String methods = send(owner, get("/api/payment-methods"), "").andReturn().getResponse().getContentAsString();
        String cash = method(methods, "CASH");
        String pix = method(methods, "PIX");
        String credit = method(methods, "CREDIT");

        send(owner, get("/api/cash-sessions/current"), "").andExpect(status().isNoContent());
        String sessionId = id(send(owner, post("/api/cash-sessions"), """
                {"openingAmountCents":10000}""").andExpect(status().isCreated()));
        send(owner, post("/api/cash-sessions"), """
                {"openingAmountCents":0}""").andExpect(status().isConflict());

        // A: 2 lanches no dinheiro. B: delivery com taxa no Pix. C: refrigerante no crédito, recebido depois.
        // D: lanche pago em dinheiro, depois cancelado e estornado: fica fora de tudo.
        String orderA = order(owner, "TAKEOUT", burger, 2, 0, cash, 6000, true);
        order(owner, "DELIVERY", burger, 1, 500, pix, 3500, true);
        String orderC = order(owner, "TAKEOUT", soda, 1, 0, credit, 700, false);
        String orderD = order(owner, "TAKEOUT", burger, 1, 0, cash, 3000, true);
        String paymentC = JsonPath.read(send(owner, get("/api/orders/" + orderC + "/payments"), "")
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        send(owner, patch("/api/orders/" + orderC + "/payments/" + paymentC), """
                {"status":"PAID"}""").andExpect(status().isOk());
        String paymentD = JsonPath.read(send(owner, get("/api/orders/" + orderD + "/payments"), "")
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        send(owner, patch("/api/orders/" + orderD + "/payments/" + paymentD), """
                {"status":"CANCELLED"}""").andExpect(status().isOk());
        send(owner, patch("/api/orders/" + orderD + "/status"), """
                {"status":"CANCELLED","reason":"Cliente desistiu","version":0}""").andExpect(status().isOk());
        send(owner, patch("/api/orders/" + orderA + "/status"), """
                {"status":"IN_PREPARATION","version":0}""").andExpect(status().isOk());
        send(owner, patch("/api/orders/" + orderA + "/status"), """
                {"status":"READY","version":1}""").andExpect(status().isOk());

        send(owner, post("/api/cash-sessions/" + sessionId + "/movements"), """
                {"type":"WITHDRAWAL","amountCents":2000,"reason":"Depósito no banco"}""")
                .andExpect(status().isCreated());
        send(owner, post("/api/cash-sessions/" + sessionId + "/movements"), """
                {"type":"DEPOSIT","amountCents":500,"reason":"Troco"}""").andExpect(status().isCreated());
        send(owner, post("/api/cash-sessions/" + sessionId + "/movements"), """
                {"type":"WITHDRAWAL","amountCents":999999,"reason":"Demais"}""")
                .andExpect(status().is4xxClientError());

        // Dinheiro: 100 de troco + 60 do pedido A - 20 de sangria + 5 de suprimento = 145.
        send(owner, get("/api/cash-sessions/current"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[?(@.type == 'CASH')].expectedCents").value(14500))
                .andExpect(jsonPath("$.lines[?(@.type == 'CASH')].paymentsCents").value(6000))
                .andExpect(jsonPath("$.lines[?(@.type == 'PIX')].expectedCents").value(3500))
                .andExpect(jsonPath("$.lines[?(@.type == 'CREDIT')].expectedCents").value(700))
                .andExpect(jsonPath("$.movements.length()").value(2));

        String closed = send(owner, patch("/api/cash-sessions/" + sessionId), """
                {"counts":[{"paymentMethodId":"%s","countedCents":14000},
                           {"paymentMethodId":"%s","countedCents":3500},
                           {"paymentMethodId":"%s","countedCents":700}],"notes":"Faltou uma nota de 5"}"""
                .formatted(cash, pix, credit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.differenceCents").value(-500))
                .andExpect(jsonPath("$.lines[?(@.type == 'CASH')].differenceCents").value(-500))
                .andReturn().getResponse().getContentAsString();

        // O esperado bate com os pagamentos recebidos na loja, somados direto no banco.
        String storeId = jdbc.queryForObject("select cast(store_id as varchar(36)) from orders where id = ?",
                String.class, UUID.fromString(orderA));
        Long received = jdbc.queryForObject("""
                select sum(amount_cents) from payment
                where store_id = ? and status = 'PAID' and origin = 'LOCAL'""", Long.class, UUID.fromString(storeId));
        long expected = ((Number) JsonPath.read(closed, "$.expectedCents")).longValue();
        assertThat(expected).isEqualTo(received + 10000 - 2000 + 500);
        send(owner, get("/api/cash-sessions/current"), "").andExpect(status().isNoContent());
        send(owner, get("/api/cash-sessions"), "")
                .andExpect(jsonPath("$.content[0].differenceCents").value(-500));
        send(owner, post("/api/cash-sessions/" + sessionId + "/movements"), """
                {"type":"DEPOSIT","amountCents":100,"reason":"Depois de fechado"}""")
                .andExpect(status().is4xxClientError());

        // Dashboard contra a consulta manual.
        Map<String, Object> manual = jdbc.queryForMap("""
                select count(*) as orders, sum(total_cents) as gross, sum(delivery_fee_cents) as delivery
                from orders where store_id = ? and status <> 'CANCELLED'""", UUID.fromString(storeId));
        String dashboard = send(owner, get("/api/reports/dashboard"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orders").value(((Number) manual.get("orders")).intValue()))
                .andExpect(jsonPath("$.summary.grossCents").value(((Number) manual.get("gross")).intValue()))
                .andExpect(jsonPath("$.summary.deliveryFeeCents").value(((Number) manual.get("delivery")).intValue()))
                .andExpect(jsonPath("$.summary.grossCents").value(6000 + 3500 + 700))
                .andExpect(jsonPath("$.summary.averageTicketCents").value(3400))
                .andExpect(jsonPath("$.summary.cancelledOrders").value(1))
                .andExpect(jsonPath("$.summary.cancelledCents").value(3000))
                .andExpect(jsonPath("$.topProducts[0].name").value("X-Burger"))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(3))
                .andExpect(jsonPath("$.topProducts[1].quantity").value(1))
                .andExpect(jsonPath("$.averagePreparationSeconds").isNumber())
                .andReturn().getResponse().getContentAsString();
        List<Integer> byHour = JsonPath.read(dashboard, "$.ordersByHour");
        assertThat(byHour).hasSize(24);
        assertThat(byHour.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);

        String date = JsonPath.read(dashboard, "$.date");
        send(owner, get("/api/reports/revenue").param("from", date).param("to", date), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byDay[0].key").value(date))
                .andExpect(jsonPath("$.byDay[0].orders").value(3))
                .andExpect(jsonPath("$.bySource[0].key").value("PEDEAI"))
                .andExpect(jsonPath("$.byType[?(@.key == 'TAKEOUT')].orders").value(2))
                .andExpect(jsonPath("$.byType[?(@.key == 'DELIVERY')].totalCents").value(3500))
                .andExpect(jsonPath("$.byPaymentMethod[?(@.type == 'CASH')].totalCents").value(6000))
                .andExpect(jsonPath("$.byPaymentMethod[?(@.type == 'PIX')].totalCents").value(3500))
                .andExpect(jsonPath("$.byPaymentMethod[?(@.type == 'CREDIT')].pendingCents").value(0));
        send(owner, get("/api/reports/revenue").param("from", "2026-02-01").param("to", "2026-01-01"), "")
                .andExpect(status().is4xxClientError());

        // Quem está no caixa mexe no caixa, mas não vê o faturamento.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        send(owner, post("/api/users"), """
                {"name":"Caio","email":"caio-%s@example.com","password":"senha-do-caio","role":"CASHIER"}"""
                .formatted(suffix)).andExpect(status().isCreated());
        String cashier = login("caio-" + suffix + "@example.com", "senha-do-caio");
        send(cashier, get("/api/cash-sessions/current"), "").andExpect(status().isNoContent());
        send(cashier, get("/api/reports/dashboard"), "").andExpect(status().isForbidden());
    }

    private String order(String owner, String type, String productId, int quantity, int deliveryFee, String method,
                         int amount, boolean paid) throws Exception {
        String delivery = type.equals("DELIVERY") ? """
                "customer":{"name":"Maria","phone":"(11) 98888-0000"},
                "deliveryAddress":{"street":"Rua A","number":"1","neighborhood":"Centro","city":"São Paulo","state":"SP"},
                """ : "";
        return id(send(owner, post("/api/orders"), """
                {"type":"%s",%s"items":[{"productId":"%s","quantity":%d,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":%d,
                 "payments":[{"paymentMethodId":"%s","amountCents":%d,"paid":%s}]}"""
                .formatted(type, delivery, productId, quantity, deliveryFee, method, amount, paid))
                .andExpect(status().isCreated()));
    }

    private String product(String owner, String category, String name, int price) throws Exception {
        return id(send(owner, post("/api/products"), """
                {"categoryId":"%s","name":"%s","priceCents":%d,"optionGroupIds":[],"available":true,"active":true}"""
                .formatted(category, name, price)));
    }

    private static String method(String methods, String type) {
        List<String> ids = JsonPath.read(methods, "$[?(@.type == '" + type + "')].id");
        return ids.getFirst();
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Loja %s","ownerName":"Ana","email":"caixa-%s@example.com","password":"senha-forte-1"}
                        """.formatted(suffix, suffix)))
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
