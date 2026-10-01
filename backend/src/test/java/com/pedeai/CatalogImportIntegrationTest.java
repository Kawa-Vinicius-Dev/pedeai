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
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importação de cardápio: a planilha pré-visualiza sem gravar, grava tudo de uma vez, atualiza pelo código na segunda
 * vez e não grava nada se uma linha tiver erro. O iFood (pelo simulador) traz produtos e adicionais.
 */
@SpringBootTest(properties = "app.ifood.simulator=true")
@AutoConfigureMockMvc
class CatalogImportIntegrationTest {
    private static final String SPREADSHEET = """
            categoria;produto;preco;descricao;codigo
            Lanches;X-Burger;32,90;Pão e carne;100
            Lanches;X-Salada;29,90;;101
            Bebidas;Refrigerante;7,00;;900
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void spreadsheetIsPreviewedThenAppliedAllOrNothing() throws Exception {
        String owner = register();

        send(owner, post("/api/catalog/imports/spreadsheet"), body(SPREADSHEET, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.categoriesCreated").value(2))
                .andExpect(jsonPath("$.productsCreated").value(3))
                .andExpect(jsonPath("$.errors.length()").value(0));
        send(owner, get("/api/products"), "").andExpect(jsonPath("$.length()").value(0));

        send(owner, post("/api/catalog/imports/spreadsheet"), body(SPREADSHEET, false))
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.productsCreated").value(3));
        send(owner, get("/api/categories"), "").andExpect(jsonPath("$.length()").value(2));

        // Segunda planilha: preço novo pelo código, um produto novo, e uma linha com grupo que não existe.
        String second = """
                categoria;produto;preco;codigo;adicionais
                Lanches;X-Burger;35,00;100;
                Lanches;X-Bacon;38,00;102;
                Bebidas;Suco;9,00;901;Inexistente
                """;
        send(owner, post("/api/catalog/imports/spreadsheet"), body(second, false))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.errors[0].origin").value("linha 4"));
        send(owner, get("/api/products"), "").andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.code == '100')].priceCents").value(3290));

        send(owner, post("/api/catalog/imports/spreadsheet"), body(second.replace("Inexistente", ""), false))
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.productsUpdated").value(1))
                .andExpect(jsonPath("$.productsCreated").value(2));
        send(owner, get("/api/products"), "").andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.code == '100')].priceCents").value(3500));
    }

    @Test
    void ifoodMenuComesWithItsAddOnsAndCodes() throws Exception {
        String owner = register();
        String connection = JsonPath.read(send(owner, post("/api/integrations"), """
                {"externalMerchantId":"merchant-%s","autoConfirm":false}""".formatted(UUID.randomUUID()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        send(owner, post("/api/integrations/" + connection + "/catalog-import"), "{\"dryRun\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.productsCreated").value(3))
                .andExpect(jsonPath("$.optionGroupsCreated").value(2));
        send(owner, get("/api/option-groups"), "")
                .andExpect(jsonPath("$[?(@.name == 'Sabores')].pricingRule").value("MAX"))
                .andExpect(jsonPath("$[?(@.name == 'Sabores')].options.length()").value(2));
        send(owner, get("/api/products"), "")
                .andExpect(jsonPath("$[?(@.code == '500')].optionGroupIds.length()").value(2))
                .andExpect(jsonPath("$[?(@.code == '901')].available").value(false));
    }

    private String body(String csv, boolean dryRun) throws Exception {
        return json.writeValueAsString(Map.of("content", csv, "dryRun", dryRun));
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Import %s","ownerName":"Ana","email":"import-%s@example.com","password":"senha-forte-1"}
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
}
