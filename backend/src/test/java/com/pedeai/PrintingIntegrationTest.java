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

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pareamento do agente, token de dispositivo, impressoras e impressora do setor. Banco e segurança de verdade. */
@SpringBootTest
@AutoConfigureMockMvc
class PrintingIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void pairedAgentReportsItsPrintersAndStopsWorkingWhenRevoked() throws Exception {
        String owner = register("lia");
        String code = JsonPath.read(send(owner, post("/api/print-agents/pairing-codes"), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("\\d{6}")))
                .andReturn().getResponse().getContentAsString(), "$.code");

        String wrong = code.equals("000000") ? "111111" : "000000";
        send(null, post("/api/agent/pairings"), pairing(wrong)).andExpect(status().isUnauthorized());
        String paired = send(null, post("/api/agent/pairings"), pairing(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentName").value("Caixa"))
                .andReturn().getResponse().getContentAsString();
        String agent = "Bearer " + JsonPath.read(paired, "$.token");
        String agentId = JsonPath.read(paired, "$.agentId");
        // O código vale uma vez só.
        send(null, post("/api/agent/pairings"), pairing(code)).andExpect(status().isUnauthorized());

        // Token de agente só vale na API do agente, e login de pessoa não vale nela.
        send(agent, get("/api/orders/active"), "").andExpect(status().isUnauthorized());
        send(owner, get("/api/agent/config"), "").andExpect(status().isUnauthorized());
        send(agent, get("/api/agent/config"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printers.length()").value(0));

        send(owner, post("/api/printers"), printer(agentId, "Cozinha", "NETWORK", null))
                .andExpect(status().isUnprocessableContent());
        String printerId = id(send(owner, post("/api/printers"), printer(agentId, "Cozinha", "NETWORK", "192.168.0.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.systemName").doesNotExist()));
        String backupId = id(send(owner, post("/api/printers"), printer(agentId, "Caixa", "SYSTEM", null)));

        send(agent, get("/api/agent/config"), "")
                .andExpect(jsonPath("$.printers.length()").value(2))
                .andExpect(jsonPath("$.printers[?(@.name == 'Cozinha')].escPosCodepage").value(3))
                .andExpect(jsonPath("$.printers[?(@.name == 'Cozinha')].charset").value("IBM860"));
        send(agent, put("/api/agent/status"), """
                {"agentVersion":"0.2.0","printers":[{"printerId":"%s","status":"ONLINE"},
                  {"printerId":"%s","status":"ERROR","detail":"Sem papel"}]}""".formatted(printerId, backupId))
                .andExpect(status().isNoContent());
        send(owner, get("/api/printers"), "")
                .andExpect(jsonPath("$[?(@.name == 'Cozinha')].status").value("ONLINE"))
                .andExpect(jsonPath("$[?(@.name == 'Caixa')].statusDetail").value("Sem papel"));
        send(owner, get("/api/print-agents"), "")
                .andExpect(jsonPath("$[0].online").value(true))
                .andExpect(jsonPath("$[0].agentVersion").value("0.2.0"));

        String kitchen = sectorId(owner);
        send(owner, put("/api/sectors/" + kitchen + "/printer"), """
                {"printerId":"%s","backupPrinterId":"%s","copies":1,"enabled":true}""".formatted(printerId, printerId))
                .andExpect(status().isUnprocessableContent());
        send(owner, put("/api/sectors/" + kitchen + "/printer"), """
                {"printerId":"%s","backupPrinterId":"%s","copies":2,"enabled":true}""".formatted(printerId, backupId))
                .andExpect(status().isOk());
        send(owner, get("/api/sector-printers"), "")
                .andExpect(jsonPath("$[0].sectorId").value(kitchen))
                .andExpect(jsonPath("$[0].copies").value(2));

        send(owner, delete("/api/print-agents/" + agentId), "").andExpect(status().isNoContent());
        send(agent, get("/api/agent/config"), "").andExpect(status().isUnauthorized());
        send(owner, get("/api/printers"), "").andExpect(jsonPath("$[0].status").value("OFFLINE"));
    }

    @Test
    void otherStoresAndOtherRolesCannotTouchPrinting() throws Exception {
        String storeA = register("mel");
        String storeB = register("nara");
        String code = JsonPath.read(send(storeA, post("/api/print-agents/pairing-codes"), "")
                .andReturn().getResponse().getContentAsString(), "$.code");
        String agentId = JsonPath.read(send(null, post("/api/agent/pairings"), pairing(code))
                .andReturn().getResponse().getContentAsString(), "$.agentId");
        String printerId = id(send(storeA, post("/api/printers"), printer(agentId, "Bar", "NETWORK", "10.0.0.9")));

        send(storeB, get("/api/printers"), "").andExpect(jsonPath("$.length()").value(0));
        send(storeB, post("/api/printers"), printer(agentId, "Bar", "NETWORK", "10.0.0.9"))
                .andExpect(status().isNotFound());
        send(storeB, put("/api/sectors/" + sectorId(storeB) + "/printer"), """
                {"printerId":"%s","copies":1,"enabled":true}""".formatted(printerId)).andExpect(status().isNotFound());
        send(storeB, delete("/api/print-agents/" + agentId), "").andExpect(status().isNotFound());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        send(storeA, post("/api/users"), """
                {"name":"Caio","email":"caio-%s@example.com","password":"senha-do-caio","role":"CASHIER"}"""
                .formatted(suffix)).andExpect(status().isCreated());
        String cashier = login("caio-" + suffix + "@example.com", "senha-do-caio");
        send(cashier, get("/api/printers"), "").andExpect(status().isForbidden());
        send(cashier, post("/api/print-agents/pairing-codes"), "").andExpect(status().isForbidden());
    }

    @Test
    void confirmedOrderPrintsOncePerSectorThroughTheAgentQueue() throws Exception {
        String owner = register("olga");
        String paired = pairAgent(owner);
        String agent = "Bearer " + JsonPath.read(paired, "$.token");
        String agentId = JsonPath.read(paired, "$.agentId");
        String kitchenPrinter = id(send(owner, post("/api/printers"), printer(agentId, "Cozinha", "NETWORK", "10.0.0.5")));
        String backupPrinter = id(send(owner, post("/api/printers"), printer(agentId, "Caixa", "SYSTEM", null)));
        String kitchen = sectorId(owner);
        String bar = id(send(owner, post("/api/sectors"), """
                {"name":"Bar","defaultSector":false,"active":true}"""));
        send(owner, put("/api/sectors/" + kitchen + "/printer"), """
                {"printerId":"%s","backupPrinterId":"%s","copies":1,"enabled":true}"""
                .formatted(kitchenPrinter, backupPrinter)).andExpect(status().isOk());
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        String burger = product(owner, category, null, "10", "X-Burger");
        String beer = product(owner, category, bar, "20", "Cerveja");

        // O Bar não tem impressora: só a cozinha imprime, e o pedido é confirmado mesmo assim.
        String orderId = id(send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","customer":{"name":"Rita"},"items":[{"productId":"%s","quantity":2,"options":[]},
                 {"productId":"%s","quantity":1,"options":[]}],"discountCents":0,"deliveryFeeCents":0,"payments":[]}"""
                .formatted(burger, beer)).andExpect(status().isCreated()));
        send(owner, get("/api/orders/" + orderId + "/print-jobs"), "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].printerId").value(kitchenPrinter))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].preview").value(containsString("2x X-BURGER")))
                .andExpect(jsonPath("$[0].preview").value(not(containsString("CERVEJA"))));

        String jobs = send(agent, get("/api/agent/jobs"), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(jobs, "$[0].id");
        byte[] payload = Base64.getDecoder().decode((String) JsonPath.read(jobs, "$[0].payload"));
        assertThat(payload).startsWith(0x1B, '@', 0x1B, 't', 3);

        send(agent, patch("/api/agent/jobs/" + jobId), """
                {"status":"SENT"}""").andExpect(status().isNoContent());
        send(agent, patch("/api/agent/jobs/" + jobId), """
                {"status":"SENT"}""").andExpect(status().isConflict());
        send(agent, get("/api/agent/jobs"), "").andExpect(jsonPath("$.length()").value(0));
        send(agent, patch("/api/agent/jobs/" + jobId), """
                {"status":"PRINTED"}""").andExpect(status().isNoContent());
        // A confirmação repetida (resposta perdida na rede) não dá erro.
        send(agent, patch("/api/agent/jobs/" + jobId), """
                {"status":"PRINTED"}""").andExpect(status().isNoContent());
        send(owner, get("/api/orders/" + orderId + "/print-jobs"), "")
                .andExpect(jsonPath("$[0].status").value("PRINTED"));

        // Principal com problema e reserva ok: o próximo pedido vai para a reserva.
        send(agent, put("/api/agent/status"), """
                {"printers":[{"printerId":"%s","status":"OFFLINE"},{"printerId":"%s","status":"ONLINE"}]}"""
                .formatted(kitchenPrinter, backupPrinter)).andExpect(status().isNoContent());
        String second = id(send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(burger)));
        send(owner, get("/api/orders/" + second + "/print-jobs"), "")
                .andExpect(jsonPath("$[0].printerId").value(backupPrinter));

        // Cancelado antes de imprimir: o pendente não sai mais.
        send(owner, patch("/api/orders/" + second + "/status"), """
                {"status":"CANCELLED","reason":"Cliente desistiu"}""").andExpect(status().isOk());
        send(owner, get("/api/orders/" + second + "/print-jobs"), "")
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
        // Outra loja não vê as impressões do pedido.
        send(register("quel"), get("/api/orders/" + orderId + "/print-jobs"), "").andExpect(status().isNotFound());
    }

    @Test
    void orderWaitingForAcceptancePrintsOnlyWhenConfirmed() throws Exception {
        String owner = register("paula");
        String agentId = JsonPath.read(pairAgent(owner), "$.agentId");
        String printerId = id(send(owner, post("/api/printers"), printer(agentId, "Cozinha", "NETWORK", "10.0.0.5")));
        String kitchen = sectorId(owner);
        send(owner, put("/api/sectors/" + kitchen + "/printer"), """
                {"printerId":"%s","copies":2,"enabled":true}""".formatted(printerId)).andExpect(status().isOk());
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        String burger = product(owner, category, null, "10", "X-Burger");
        send(owner, patch("/api/store"), """
                {"autoConfirmOwnOrders":false}""").andExpect(status().isOk());

        String orderId = id(send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(burger))
                .andExpect(jsonPath("$.status").value("RECEIVED")));
        send(owner, get("/api/orders/" + orderId + "/print-jobs"), "").andExpect(jsonPath("$.length()").value(0));

        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"CONFIRMED"}""").andExpect(status().isOk());
        send(owner, patch("/api/orders/" + orderId + "/status"), """
                {"status":"IN_PREPARATION"}""").andExpect(status().isOk());
        // Uma impressão só, mesmo com o pedido andando; as 2 cópias vão no mesmo trabalho.
        send(owner, get("/api/orders/" + orderId + "/print-jobs"), "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reason").value("AUTO"));
    }

    @Test
    void panelShowsWhatNeedsAttentionAndPrintsItAgain() throws Exception {
        String owner = register("rosa");
        String paired = pairAgent(owner);
        String agent = "Bearer " + JsonPath.read(paired, "$.token");
        String agentId = JsonPath.read(paired, "$.agentId");
        String kitchenPrinter = id(send(owner, post("/api/printers"), printer(agentId, "Cozinha", "NETWORK", "10.0.0.5")));
        String cashierPrinter = id(send(owner, post("/api/printers"), printer(agentId, "Caixa", "SYSTEM", null)));
        String kitchen = sectorId(owner);
        send(owner, put("/api/sectors/" + kitchen + "/printer"), """
                {"printerId":"%s","copies":1,"enabled":true}""".formatted(kitchenPrinter)).andExpect(status().isOk());
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        String burger = product(owner, category, null, "10", "X-Burger");
        String orderId = id(send(owner, post("/api/orders"), """
                {"type":"TAKEOUT","items":[{"productId":"%s","quantity":1,"options":[]}],
                 "discountCents":0,"deliveryFeeCents":0,"payments":[]}""".formatted(burger)));

        // Impressora da cozinha caiu com o pedido na fila: alerta com a contagem.
        send(agent, put("/api/agent/status"), """
                {"printers":[{"printerId":"%s","status":"OFFLINE"}]}""".formatted(kitchenPrinter))
                .andExpect(status().isNoContent());
        send(owner, get("/api/print-alerts"), "")
                .andExpect(jsonPath("$[0].message").value("Impressora Cozinha offline: 1 impressão aguardando."));

        // O agente caiu no meio da impressão: fica incerto, e a pessoa manda para o Caixa.
        String jobId = JsonPath.read(send(agent, get("/api/agent/jobs"), "").andReturn().getResponse()
                .getContentAsString(), "$[0].id");
        send(agent, patch("/api/agent/jobs/" + jobId), """
                {"status":"SENT"}""").andExpect(status().isNoContent());
        send(agent, patch("/api/agent/jobs/" + jobId), """
                {"status":"UNCERTAIN","error":"Agente reiniciou"}""").andExpect(status().isNoContent());
        send(owner, get("/api/print-alerts"), "")
                .andExpect(jsonPath("$[?(@.printerId == null)].message").value("1 impressão precisa de atenção."));
        send(owner, get("/api/print-jobs"), "")
                .andExpect(jsonPath("$[0].status").value("UNCERTAIN"))
                .andExpect(jsonPath("$[0].title").value(containsString("Cozinha")));

        send(owner, post("/api/print-jobs/" + jobId + "/retry").param("printerId", cashierPrinter), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.printerId").value(cashierPrinter));
        send(owner, post("/api/print-jobs/" + jobId + "/retry"), "").andExpect(status().isConflict());
        send(agent, get("/api/agent/jobs"), "")
                .andExpect(jsonPath("$[0].printerId").value(cashierPrinter))
                .andExpect(jsonPath("$[0].id").value(jobId));

        // Reimpressão: faixa REIMPRESSÃO, e o duplo clique (mesma chave) não imprime duas vezes.
        String reprint = """
                {"documentType":"ORDER_TICKET","printerId":"%s"}""".formatted(cashierPrinter);
        String first = JsonPath.read(send(owner, post("/api/orders/" + orderId + "/print-jobs")
                        .header("Idempotency-Key", "clique-1"), reprint)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("REPRINT"))
                .andExpect(jsonPath("$.preview").value(containsString("*** REIMPRESSÃO ***")))
                .andReturn().getResponse().getContentAsString(), "$.id");
        send(owner, post("/api/orders/" + orderId + "/print-jobs").header("Idempotency-Key", "clique-1"), reprint)
                .andExpect(jsonPath("$.id").value(first));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        send(owner, post("/api/users"), """
                {"name":"Chef","email":"chef-%s@example.com","password":"senha-do-chef","role":"KITCHEN"}"""
                .formatted(suffix)).andExpect(status().isCreated());
        String kitchenUser = login("chef-" + suffix + "@example.com", "senha-do-chef");
        send(kitchenUser, post("/api/orders/" + orderId + "/print-jobs"), reprint).andExpect(status().isForbidden());
        send(kitchenUser, post("/api/orders/" + orderId + "/print-jobs"), """
                {"documentType":"PRODUCTION_TICKET","sectorId":"%s","printerId":"%s"}"""
                .formatted(kitchen, kitchenPrinter)).andExpect(status().isCreated());
        send(register("sara"), get("/api/print-jobs"), "").andExpect(jsonPath("$.length()").value(0));
    }

    private String pairAgent(String owner) throws Exception {
        String code = JsonPath.read(send(owner, post("/api/print-agents/pairing-codes"), "")
                .andReturn().getResponse().getContentAsString(), "$.code");
        return send(null, post("/api/agent/pairings"), pairing(code)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String product(String owner, String category, String sector, String code, String name) throws Exception {
        return id(send(owner, post("/api/products"), """
                {"categoryId":"%s","sectorId":%s,"code":"%s","name":"%s","priceCents":1000,"optionGroupIds":[],
                 "available":true,"active":true}"""
                .formatted(category, sector == null ? "null" : "\"" + sector + "\"", code, name)));
    }

    private static String pairing(String code) {
        return """
                {"code":"%s","name":"Caixa","os":"Windows 11","agentVersion":"0.1.0"}""".formatted(code);
    }

    private static String printer(String agentId, String name, String connection, String host) {
        return """
                {"agentId":"%s","name":"%s","connectionType":"%s","host":%s,"port":9100,
                 "systemName":"ELGIN i9","paperWidthMm":80,"columns":48,"codepage":"PC860",
                 "cutMode":"PARTIAL","active":true}"""
                .formatted(agentId, name, connection, host == null ? "null" : "\"" + host + "\"");
    }

    private String sectorId(String owner) throws Exception {
        return id(send(owner, post("/api/sectors"), """
                {"name":"Cozinha","defaultSector":true,"active":true}"""));
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
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (!body.isEmpty()) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
