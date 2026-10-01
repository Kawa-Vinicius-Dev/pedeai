package com.pedeai.integration.opendelivery;

import com.pedeai.integration.config.OpenDeliveryProperties;
import com.pedeai.order.domain.OrderSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Chamadas à Order API de um app Open Delivery (especificação v1.7): token OAuth client credentials, polling de
 * eventos, acknowledgment, detalhes do pedido e as ações de status. O app é escolhido pelo provider do vínculo.
 */
@Component
public class OpenDeliveryClient {
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(5);

    /** Resposta de erro do app. Rede, 429 e 5xx valem nova tentativa; o resto é recusa. */
    public static class OpenDeliveryApiException extends RuntimeException {
        private final int status;

        public OpenDeliveryApiException(int status, String body) {
            super("O app respondeu " + status + (body == null || body.isBlank() ? "" : ": " + cut(body)));
            this.status = status;
        }

        public boolean retryable() {
            return status == 0 || status == 429 || status >= 500;
        }

        private static String cut(String body) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
    }

    private record Token(String value, Instant expiresAt) {
    }

    private final OpenDeliveryProperties properties;
    private final Clock clock;
    private final Map<OrderSource, Token> tokens = new ConcurrentHashMap<>();

    public OpenDeliveryClient(OpenDeliveryProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Eventos novos de um merchant. 204: nada novo. */
    public List<JsonNode> poll(OrderSource provider, String merchantId) {
        OpenDeliveryProperties.App app = app(provider);
        ResponseEntity<JsonNode> response = call(provider, () -> http(app).get().uri(app.pollingPath())
                .headers(headers -> {
                    auth(provider, headers);
                    headers.set("x-polling-merchants", merchantId);
                })
                .retrieve().onStatus(HttpStatusCode::isError, OpenDeliveryClient::fail).toEntity(JsonNode.class));
        List<JsonNode> events = new ArrayList<>();
        if (response.getBody() != null && response.getBody().isArray()) {
            response.getBody().forEach(events::add);
        }
        return events;
    }

    /** Confirma o recebimento dos eventos do polling (até 100 por chamada). Webhook não tem ack. */
    public void acknowledge(OrderSource provider, List<JsonNode> events) {
        OpenDeliveryProperties.App app = app(provider);
        for (int start = 0; start < events.size(); start += 100) {
            List<Map<String, String>> batch = events.subList(start, Math.min(events.size(), start + 100)).stream()
                    .map(event -> Map.of("id", event.path("eventId").asString(), "orderId",
                            event.path("orderId").asString(), "eventType", event.path("eventType").asString()))
                    .toList();
            call(provider, () -> http(app).post().uri("/v1/events/acknowledgment")
                    .headers(headers -> auth(provider, headers)).contentType(MediaType.APPLICATION_JSON).body(batch)
                    .retrieve().onStatus(HttpStatusCode::isError, OpenDeliveryClient::fail).toBodilessEntity());
        }
    }

    public JsonNode order(OrderSource provider, String orderId) {
        OpenDeliveryProperties.App app = app(provider);
        return call(provider, () -> http(app).get().uri("/v1/orders/{id}", orderId)
                .headers(headers -> auth(provider, headers)).retrieve()
                .onStatus(HttpStatusCode::isError, OpenDeliveryClient::fail).body(JsonNode.class));
    }

    /** {@code preparing}, {@code readyForPickup} ou {@code dispatch}; {@code confirm} leva o número do pedido aqui. */
    public void action(OrderSource provider, String orderId, String action, Object body) {
        OpenDeliveryProperties.App app = app(provider);
        call(provider, () -> {
            var request = http(app).post().uri("/v1/orders/{id}/{action}", orderId, action)
                    .headers(headers -> auth(provider, headers));
            if (body != null) {
                request = request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return request.retrieve().onStatus(HttpStatusCode::isError, OpenDeliveryClient::fail).toBodilessEntity();
        });
    }

    private OpenDeliveryProperties.App app(OrderSource provider) {
        return properties.app(provider).orElseThrow(() -> new OpenDeliveryApiException(0,
                "app " + provider + " sem credenciais no servidor"));
    }

    private static RestClient http(OpenDeliveryProperties.App app) {
        return RestClient.builder().baseUrl(app.baseUrl()).build();
    }

    /** Um 401 com token em cache: o token pode ter sido revogado antes da hora. Renova e tenta uma vez mais. */
    private <T> T call(OrderSource provider, Supplier<T> request) {
        try {
            return request.get();
        } catch (OpenDeliveryApiException e) {
            if (e.status != 401) {
                throw e;
            }
            tokens.remove(provider);
            return request.get();
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new OpenDeliveryApiException(0, e.getMessage());
        }
    }

    private void auth(OrderSource provider, HttpHeaders headers) {
        headers.setBearerAuth(token(provider));
    }

    private String token(OrderSource provider) {
        Token current = tokens.get(provider);
        Instant now = Instant.now(clock);
        if (current != null && current.expiresAt().minus(RENEW_BEFORE).isAfter(now)) {
            return current.value();
        }
        OpenDeliveryProperties.App app = app(provider);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", app.clientId());
        form.add("client_secret", app.clientSecret());
        JsonNode body = http(app).post().uri("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().onStatus(HttpStatusCode::isError, OpenDeliveryClient::fail)
                .body(JsonNode.class);
        if (body == null || body.path("access_token").asString("").isBlank()) {
            throw new OpenDeliveryApiException(401, "o app não devolveu o token");
        }
        Token token = new Token(body.path("access_token").asString(),
                now.plusSeconds(body.path("expires_in").asLong(3600)));
        tokens.put(provider, token);
        return token.value();
    }

    private static void fail(org.springframework.http.HttpRequest request,
                             org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
        throw new OpenDeliveryApiException(response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }
}
