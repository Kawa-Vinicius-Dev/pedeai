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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
