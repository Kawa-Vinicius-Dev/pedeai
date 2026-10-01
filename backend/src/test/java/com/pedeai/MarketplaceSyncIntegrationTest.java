package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.service.IntegrationScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O PedeAí manda no iFood (API do iFood simulada por mock): cardápio com acréscimo, item só do balcão fora, pizza
 * recusada com explicação, a chave "Cardápio aberto" pausando e reabrindo, e o horário daqui substituindo o de lá.
 */
@SpringBootTest(properties = {"app.ifood.enabled=true", "app.ifood.client-id=pedeai", "app.ifood.client-secret=segredo"})
@AutoConfigureMockMvc
class MarketplaceSyncIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IntegrationScheduler scheduler;
    @MockitoBean
    private IfoodClient ifood;

    @Test
    @SuppressWarnings("unchecked")
    void thePedeAiMenuGoesToIfood() throws Exception {
        String owner = register();
        String merchant = "merchant-" + UUID.randomUUID();
        when(ifood.merchants()).thenReturn(List.of(new IfoodClient.Merchant(merchant, "Lanchonete")));
        when(ifood.defaultCatalogId(merchant)).thenReturn("catalogo-1");
        when(ifood.createCategory(eq(merchant), eq("catalogo-1"), eq("Lanches"), anyString())).thenReturn("cat-ifood");
        when(ifood.pause(eq(merchant), any())).thenReturn("pausa-1");

        send(owner, put("/api/store/menu-open"), "{\"open\":true}").andExpect(status().isOk());
        String category = id(send(owner, post("/api/categories"), """
                {"name":"Lanches","active":true}"""));
        product(owner, category, "10", "X-Burger", 2990, true, "[]");
        product(owner, category, "20", "Café do balcão", 500, false, "[]");
        String flavors = id(send(owner, post("/api/option-groups"), """
                {"name":"Sabores","minChoices":1,"maxChoices":2,"pricingRule":"MAX","active":true,
                 "options":[{"name":"Calabresa","priceCents":0,"available":true,"active":true}]}"""));
        product(owner, category, "30", "Pizza meio a meio", 4500, true, "[\"" + flavors + "\"]");
        String connection = id(send(owner, post("/api/integrations"), """
                {"externalMerchantId":"%s","autoConfirm":false}""".formatted(merchant)));
        send(owner, patch("/api/store"), "{\"ifoodMarkupBp\":1500}").andExpect(status().isOk());

        // Ligar a sincronização manda o cardápio inteiro.
        send(owner, patch("/api/integrations/" + connection), """
                {"status":"ACTIVE","autoConfirm":false,"catalogSync":true}""")
                .andExpect(jsonPath("$.catalogSync").value(true))
                .andExpect(jsonPath("$.syncPending").value(4));
        scheduler.sendMarketplaceSync();

        ArgumentCaptor<Map<String, Object>> sent = ArgumentCaptor.forClass(Map.class);
        verify(ifood, atLeastOnce()).putItem(eq(merchant), sent.capture());
        List<Map<String, Object>> items = sent.getAllValues().stream()
                .map(body -> (Map<String, Object>) body.get("item")).toList();
        assertThat(items).extracting(item -> item.get("externalCode")).containsOnly("10");
        assertThat(items.getFirst().get("categoryId")).isEqualTo("cat-ifood");
        assertThat(((Map<String, Object>) items.getFirst().get("price")).get("value"))
                .isEqualTo(new BigDecimal("34.90"));
        verify(ifood, times(1)).createCategory(eq(merchant), eq("catalogo-1"), eq("Lanches"), anyString());
        send(owner, get("/api/integrations"), "")
                .andExpect(jsonPath("$[0].syncPending").value(0))
                .andExpect(jsonPath("$[0].syncFailed").value(1))
                .andExpect(jsonPath("$[0].lastError").value(containsString("Pizza meio a meio")));

        // Uma chave fecha tudo: fechar o cardápio pausa o iFood; abrir de novo tira a pausa.
        verify(ifood, never()).pause(anyString(), any());
        send(owner, put("/api/store/menu-open"), "{\"open\":false}").andExpect(status().isOk());
        scheduler.sendMarketplaceSync();
        verify(ifood).pause(eq(merchant), any());
        send(owner, put("/api/store/menu-open"), "{\"open\":true}").andExpect(status().isOk());
        scheduler.sendMarketplaceSync();
        verify(ifood).resume(merchant, "pausa-1");

        // O horário daqui substitui o de lá; passar da meia-noite é só uma duração maior.
        send(owner, patch("/api/store"), """
                {"openingHours":[{"dayOfWeek":5,"opensAt":"18:00","closesAt":"02:00"}]}""")
                .andExpect(status().isOk());
        scheduler.sendMarketplaceSync();
        verify(ifood).openingHours(merchant, List.of(Map.of("dayOfWeek", "FRIDAY", "start", "18:00:00",
                "duration", 480L)));
    }

    private void product(String owner, String category, String code, String name, long price, boolean ifood,
                         String groups) throws Exception {
        send(owner, post("/api/products"), """
                {"categoryId":"%s","code":"%s","name":"%s","priceCents":%d,"optionGroupIds":%s,
                 "available":true,"active":true,"sellOnIfood":%s}""".formatted(category, code, name, price, groups,
                ifood)).andExpect(status().isCreated());
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Sync %s","ownerName":"Ana","email":"sync-%s@example.com","password":"senha-forte-1"}
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
