package com.pedeai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O frontend gera os tipos TypeScript a partir de frontend/openapi.json. Se a API mudar e o arquivo
 * não for atualizado, este teste falha antes de os tipos ficarem mentindo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {
    private static final Path FRONTEND_SPEC = Path.of("..", "frontend", "openapi.json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void frontendSpecMatchesTheApi() throws Exception {
        String live = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode expected = objectMapper.readTree(Files.readString(FRONTEND_SPEC));
        JsonNode actual = objectMapper.readTree(live);

        assertThat(actual)
                .withFailMessage("frontend/openapi.json está desatualizado. Com a API rodando, execute no "
                        + "frontend: npm run api:spec && npm run api:types")
                .isEqualTo(expected);
    }
}
