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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cardápio digital de ponta a ponta: o cliente, sem login, vê o cardápio público, pede com preço e taxa calculados
 * no servidor, a loja aceita no quadro e o cliente acompanha pelo código.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DigitalMenuIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerOrdersFromThePublicMenuAndFollowsIt() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = register("Pizzaria Bella " + suffix, "dono-" + suffix);
        String slug = JsonPath.read(send(owner, get("/api/store"), "").andReturn().getResponse().getContentAsString(),
                "$.slug");
        org.assertj.core.api.Assertions.assertThat(slug).isEqualTo("pizzaria-bella-" + suffix);

        send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}""").andExpect(status().isCreated());
        String pizzas = id(send(owner, post("/api/categories"), """
                {"name":"Pizzas","active":true}"""));
        String hiddenCategory = id(send(owner, post("/api/categories"), """
                {"name":"Fora do cardápio","active":false}"""));
        String flavors = send(owner, post("/api/option-groups"), """
                {"name":"Sabores","minChoices":1,"maxChoices":2,"pricingRule":"MAX","active":true,"options":[
                  {"code":"101","name":"Calabresa","priceCents":4590,"available":true,"active":true},
                  {"code":"102","name":"Quatro queijos","priceCents":5290,"available":true,"active":true}]}""")
                .andReturn().getResponse().getContentAsString();
        List<String> flavorIds = JsonPath.read(flavors, "$.options[*].id");
        String pizza = id(send(owner, post("/api/products"), """
                {"categoryId":"%s","name":"Pizza Grande","priceCents":0,"optionGroupIds":["%s"],
                 "available":true,"active":true}""".formatted(pizzas, JsonPath.read(flavors, "$.id"))));
        String soda = product(owner, pizzas, "Refrigerante lata", 700, true);
        String retired = product(owner, pizzas, "Pizza antiga", 3000, false);
        String secret = product(owner, hiddenCategory, "Prato do dono", 1000, true);
        send(owner, post("/api/delivery-zones"), """
                {"neighborhood":"Centro","feeCents":800,"active":true}""").andExpect(status().isCreated());

        // O cardápio público: sem login, sem o que foi retirado e sem pagamento "online de marketplace".
        String menu = mockMvc.perform(get("/api/public/stores/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].products.length()").value(2))
                .andExpect(jsonPath("$.categories[0].products[?(@.id == '%s')]".formatted(retired)).isEmpty())
                .andExpect(jsonPath("$.optionGroups[0].options.length()").value(2))
                .andExpect(jsonPath("$.deliveryZones[0].feeCents").value(800))
                .andExpect(jsonPath("$.paymentMethods[?(@.type == 'ONLINE')]").isEmpty())
                .andReturn().getResponse().getContentAsString();
        List<String> cash = JsonPath.read(menu, "$.paymentMethods[?(@.type == 'CASH')].id");
        String order = """
                {"type":"DELIVERY","customerName":"Maria","customerPhone":"(11) 98888-0000",
                 "deliveryAddress":{"street":"Rua das Flores","number":"120","neighborhood":"centro",
                                    "city":"São Paulo","state":"SP"},
                 "items":[{"productId":"%s","quantity":1,"options":[{"optionId":"%s","quantity":1},
                                                                   {"optionId":"%s","quantity":1}]},
                          {"productId":"%s","quantity":2,"options":[]}],
                 "notes":"Interfone quebrado","paymentMethodId":"%s","changeForCents":10000}"""
                .formatted(pizza, flavorIds.get(0), flavorIds.get(1), soda, cash.getFirst());

        mockMvc.perform(post("/api/public/stores/" + slug + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("A loja não está recebendo pedidos pelo cardápio agora."));

        // Quem está no caixa abre o cardápio durante o serviço.
        send(owner, put("/api/store/menu-open"), "{\"open\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.menuOpen").value(true));

        mockMvc.perform(post("/api/public/stores/" + slug + "/products/" + pizza + "/price-quotes")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity":1,"options":[{"optionId":"%s","quantity":1},{"optionId":"%s","quantity":1}]}"""
                                .formatted(flavorIds.get(0), flavorIds.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPriceCents").value(5290));

        // Pizza pelo sabor mais caro (52,90) + 2 refrigerantes (14,00) + entrega do Centro (8,00).
        String placed = mockMvc.perform(post("/api/public/stores/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.totalCents").value(5290 + 1400 + 800))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(placed, "$.trackingCode");

        String board = send(owner, get("/api/orders/active"), "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].source").value("DIGITAL_MENU"))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(board, "$[0].id");
        send(owner, get("/api/orders/" + orderId), "")
                .andExpect(jsonPath("$.deliveryFeeCents").value(800))
                .andExpect(jsonPath("$.deliveryAddress.neighborhood").value("Centro"))
                .andExpect(jsonPath("$.customerPhone").value("+5511988880000"))
                .andExpect(jsonPath("$.trackingCode").value(code));
        send(owner, get("/api/orders/" + orderId + "/payments"), "")
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].amountCents").value(7490))
                .andExpect(jsonPath("$[0].changeCents").value(2510));
        send(owner, get("/api/orders/" + orderId + "/history"), "")
                .andExpect(jsonPath("$[0].actorType").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].actorName").value("Maria"));

        // O cliente acompanha sem ver dados pessoais de ninguém.
        mockMvc.perform(get("/api/public/orders/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.storeSlug").value(slug))
                .andExpect(jsonPath("$.items[0].details").value("Calabresa, Quatro queijos"))
                .andExpect(jsonPath("$.customerPhone").doesNotExist())
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist());

        // A loja aceita no quadro e depois cancela: pedido do cardápio é da própria loja, não do marketplace.
        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CONFIRMED","version":0}""").andExpect(status().isOk());
        mockMvc.perform(get("/api/public/orders/" + code)).andExpect(jsonPath("$.status").value("CONFIRMED"));
        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CANCELLED","reason":"Acabou a massa","version":1}""").andExpect(status().isOk());
        mockMvc.perform(get("/api/public/orders/" + code))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Acabou a massa"));

        // O que o cardápio não oferece não entra por fora.
        mockMvc.perform(post("/api/public/stores/" + slug + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order.replace("centro", "Bairro Longe")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/public/stores/" + slug + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order.replace(soda, secret)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/api/public/stores/nao-existe")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/orders/codigo-que-nao-existe")).andExpect(status().isNotFound());

        // O dono troca o endereço; outra loja não pode pegar o mesmo.
        send(owner, patch("/api/store"), "{\"slug\":\"bella-" + suffix + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("bella-" + suffix));
        String other = register("Outra " + suffix, "outra-" + suffix);
        send(other, patch("/api/store"), "{\"slug\":\"bella-" + suffix + "\"}").andExpect(status().isConflict());
        send(other, patch("/api/store"), "{\"slug\":\"Com Espaço\"}").andExpect(status().isBadRequest());
    }

    @Test
    void openingHoursCloseTheMenuAndAutoConfirmSendsTheOrderStraightToTheKitchen() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = register("Lanches " + suffix, "lanches-" + suffix);
        String slug = JsonPath.read(send(owner, get("/api/store"), "").andReturn().getResponse().getContentAsString(),
                "$.slug");
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        String burger = product(owner, category, "X-Burger", 3000, true);
        send(owner, put("/api/store/menu-open"), "{\"open\":true}").andExpect(status().isOk());
        String methods = mockMvc.perform(get("/api/public/stores/" + slug)).andReturn().getResponse()
                .getContentAsString();
        List<String> cash = JsonPath.read(methods, "$.paymentMethods[?(@.type == 'CASH')].id");
        String order = """
                {"type":"TAKEOUT","customerName":"João","customerPhone":"11977770000",
                 "items":[{"productId":"%s","quantity":1,"options":[]}],"paymentMethodId":"%s"}"""
                .formatted(burger, cash.getFirst());

        // Todo dia, só daqui a 2 horas: com a chave ligada, o cardápio continua fechado pelo horário.
        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        send(owner, patch("/api/store"), week(now.plusHours(2), now.plusHours(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingHours.length()").value(7));
        mockMvc.perform(get("/api/public/stores/" + slug))
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.openingHours.length()").value(7));
        mockMvc.perform(post("/api/public/stores/" + slug + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content(order)).andExpect(status().isUnprocessableContent());

        // Dentro do horário e com aceite automático: o pedido já nasce confirmado.
        String within = week(now.minusHours(1), now.plusHours(1));
        send(owner, patch("/api/store"), within.substring(0, within.length() - 1) + ",\"menuAutoConfirm\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.menuAutoConfirm").value(true));
        mockMvc.perform(get("/api/public/stores/" + slug)).andExpect(jsonPath("$.open").value(true));
        String code = JsonPath.read(mockMvc.perform(post("/api/public/stores/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.trackingCode");
        mockMvc.perform(get("/api/public/orders/" + code)).andExpect(jsonPath("$.status").value("CONFIRMED"));

        send(owner, patch("/api/store"), "{\"openingHours\":[]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.openingHours.length()").value(0));
        send(owner, patch("/api/store"), """
                {"openingHours":[{"dayOfWeek":1,"opensAt":"10:00","closesAt":"14:00"},
                                 {"dayOfWeek":1,"opensAt":"18:00","closesAt":"23:00"}]}""")
                .andExpect(status().isUnprocessableContent());
    }

    /** O mesmo horário nos sete dias da semana, como corpo do PATCH /api/store. */
    private static String week(java.time.LocalTime opens, java.time.LocalTime closes) {
        String hours = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> "{\"dayOfWeek\":%d,\"opensAt\":\"%s\",\"closesAt\":\"%s\"}".formatted(day,
                        opens.withSecond(0).withNano(0), closes.withSecond(0).withNano(0)))
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"openingHours\":[" + hours + "]}";
    }

    private String product(String owner, String category, String name, int price, boolean active) throws Exception {
        return id(send(owner, post("/api/products"), """
                {"categoryId":"%s","name":"%s","priceCents":%d,"optionGroupIds":[],"available":true,"active":%s}"""
                .formatted(category, name, price, active)));
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
