package com.pedeai.shared.config;

import com.pedeai.shared.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * declarado no DTO com {@code @Schema(types = {"string", "null"})}, ou {@code @Schema(nullable = true)}
     * quando é outro objeto.
     */
    @Bean
    OpenApiCustomizer responseFieldsAreAlwaysPresent() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
            if (name.endsWith("Response") && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                nullableReferencesAsOneOf(schema);
            }
        });
    }

    /**
     * O springdoc escreve objeto anulável como {@code {"type": "null", "$ref": ...}}, e o gerador de tipos
     * ignora o nulo. No OpenAPI 3.1 o certo é {@code oneOf: [ref, null]}.
     */
    @SuppressWarnings("rawtypes")
    private static void nullableReferencesAsOneOf(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        properties.replaceAll((property, value) -> {
            Set<String> types = value.getTypes();
            if (value.get$ref() == null || types == null || !types.contains("null")) {
                return value;
            }
            Schema<Object> reference = new Schema<>();
            reference.set$ref(value.get$ref());
            Schema<Object> nothing = new Schema<>();
            nothing.setTypes(Set.of("null"));
            Schema<Object> nullable = new Schema<>();
            nullable.setOneOf(List.of(reference, nothing));
            return nullable;
        });
    }
}
