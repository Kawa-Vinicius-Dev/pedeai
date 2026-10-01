package com.pedeai.integration.ifood;

import com.pedeai.integration.config.IfoodProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Chamadas à Merchant API do iFood como aplicativo centralizado (grant client_credentials). O token é renovado pelo
 * {@code expiresIn} recebido, nunca por um prazo fixo; um 401 renova uma vez e repete a chamada.
 */
@Component
public class IfoodClient {
    private static final String TOKEN_PATH = "/authentication/v1.0/oauth/token";
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(5);

    /** Resposta de erro da API do iFood. 4xx de regra não adianta repetir; 5xx e 429 sim. */
    public static class IfoodApiException extends RuntimeException {
        private final int status;

        public IfoodApiException(int status, String body) {
            super("iFood respondeu " + status + (body == null || body.isBlank() ? "" : ": " + body));
            this.status = status;
        }

        public int status() {
            return status;
        }

        public boolean retryable() {
            return status == 429 || status >= 500;
        }
    }

    public record Merchant(String id, String name) {
    }

    public record CancellationReason(String code, String description) {
    }

    private final RestClient http;
    private final IfoodProperties properties;
    private final Clock clock;
    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public IfoodClient(IfoodProperties properties, Clock clock) {
        this.http = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.clock = clock;
    }

    public List<Merchant> merchants() {
        JsonNode body = call(() -> http.get().uri("/merchant/v1.0/merchants").headers(this::auth).retrieve()
                .onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class));
        List<Merchant> merchants = new ArrayList<>();
        if (body != null) {
            body.forEach(merchant -> merchants.add(new Merchant(merchant.path("id").asString(),
                    merchant.path("name").asString())));
        }
        return merchants;
    }

    /** Eventos pendentes dos merchants informados (até 100 por chamada). 204: nada novo. */
    public List<JsonNode> poll(Collection<String> merchantIds) {
        ResponseEntity<JsonNode> response = call(() -> http.get().uri(properties.pollingPath())
                .headers(headers -> {
                    auth(headers);
                    headers.set("x-polling-merchants", String.join(",", merchantIds));
                })
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toEntity(JsonNode.class));
        List<JsonNode> events = new ArrayList<>();
        if (response.getBody() != null && response.getBody().isArray()) {
            response.getBody().forEach(events::add);
        }
        return events;
    }

    /** Confirma o recebimento: o evento não volta no próximo polling. Chamar só depois de gravar no inbox. */
    public void acknowledge(Collection<String> eventIds) {
        if (eventIds.isEmpty()) {
            return;
        }
        call(() -> http.post().uri(properties.acknowledgmentPath()).headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("acknowledgedEventIds", List.copyOf(eventIds)))
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toBodilessEntity());
    }

    public JsonNode order(String orderId) {
        return call(() -> http.get().uri("/order/v1.0/orders/{id}", orderId).headers(this::auth).retrieve()
                .onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class));
    }

    /** confirm, startPreparation, readyToPickup ou dispatch. */
    public void orderAction(String orderId, String action) {
        call(() -> http.post().uri("/order/v1.0/orders/{id}/{action}", orderId, action).headers(this::auth)
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toBodilessEntity());
    }

    public List<CancellationReason> cancellationReasons(String orderId) {
        JsonNode body = call(() -> http.get().uri("/order/v1.0/orders/{id}/cancellationReasons", orderId)
                .headers(this::auth).retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail)
                .body(JsonNode.class));
        List<CancellationReason> reasons = new ArrayList<>();
        if (body != null) {
            body.forEach(reason -> reasons.add(new CancellationReason(
                    reason.path("cancelCodeId").asString(reason.path("code").asString()),
                    reason.path("description").asString())));
        }
        return reasons;
    }

    public void requestCancellation(String orderId, String code, String description) {
        call(() -> http.post().uri("/order/v1.0/orders/{id}/requestCancellation", orderId).headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", description, "cancellationCode", code))
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toBodilessEntity());
    }

    /** Um 401 com token em cache: o token pode ter sido revogado antes da hora. Renova e tenta uma vez mais. */
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (IfoodApiException e) {
            if (e.status() != 401) {
                throw e;
            }
            synchronized (this) {
                tokenExpiresAt = Instant.EPOCH;
            }
            return request.get();
        } catch (RestClientException e) {
            throw new IfoodApiException(503, e.getMessage());
        }
    }

    private void auth(HttpHeaders headers) {
        headers.setBearerAuth(token());
    }

    private synchronized String token() {
        Instant now = Instant.now(clock);
        if (token != null && now.isBefore(tokenExpiresAt.minus(RENEW_BEFORE))) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grantType", "client_credentials");
        form.add("clientId", properties.clientId());
        form.add("clientSecret", properties.clientSecret());
        JsonNode body = http.post().uri(TOKEN_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class);
        if (body == null || body.path("accessToken").asString().isBlank()) {
            throw new IfoodApiException(502, "resposta de token sem accessToken");
        }
        token = body.path("accessToken").asString();
        tokenExpiresAt = now.plusSeconds(body.path("expiresIn").asLong(3600));
        return token;
    }

    private static void fail(org.springframework.http.HttpRequest request,
                             org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        throw new IfoodApiException(response.getStatusCode().value(), body.length() > 200 ? body.substring(0, 200)
                : body);
    }
}
