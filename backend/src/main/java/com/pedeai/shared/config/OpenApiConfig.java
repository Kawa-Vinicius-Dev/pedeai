package com.pedeai.shared.config;

import com.pedeai.shared.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

@Configuration
public class OpenApiConfig {
    static {
        // CurrentUser vem do token, não da requisição: não pode aparecer como parâmetro na documentação.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI pedeAiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PedeAí API")
                        .version("v1")
                        .description("API de gestão de pedidos para restaurantes."))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                // URL relativa: a especificação fica igual em qualquer ambiente (e versionável no frontend).
                .addServersItem(new Server().url("/"));
    }

    /**
     * Toda resposta da API devolve todos os campos (nulos aparecem como {@code null}). Marcar os campos
     * como obrigatórios faz os tipos gerados no frontend refletirem isso. Campo que pode vir nulo é
     * declarado no DTO com {@code @Schema(types = {"string", "null"})}.
     */
    @Bean
    OpenApiCustomizer responseFieldsAreAlwaysPresent() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
            if (name.endsWith("Response") && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            }
        });
    }
}
