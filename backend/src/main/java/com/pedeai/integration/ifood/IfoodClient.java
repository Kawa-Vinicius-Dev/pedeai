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

    /** Pausa a loja no iFood agora (até reabrir pelo PedeAí, no máximo 12 h). Devolve o id da pausa. */
    public String pause(String merchantId, Instant now) {
        JsonNode body = call(() -> http.post().uri("/merchant/v1.0/merchants/{id}/interruptions", merchantId)
                .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("description", "Pausado pelo PedeAí", "start", now.toString(),
                        "end", now.plus(java.time.Duration.ofHours(12)).toString()))
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class));
        return body == null ? null : body.path("id").asString(null);
    }

    public void resume(String merchantId, String interruptionId) {
        call(() -> http.delete().uri("/merchant/v1.0/merchants/{id}/interruptions/{interruption}", merchantId,
                        interruptionId).headers(this::auth)
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toBodilessEntity());
    }

    /** Substitui o horário da loja no iFood. {@code shifts}: dia ("MONDAY"), início e duração em minutos. */
    public void openingHours(String merchantId, List<Map<String, Object>> shifts) {
        call(() -> http.put().uri("/merchant/v1.0/merchants/{id}/opening-hours", merchantId).headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("storeId", merchantId, "shifts", shifts))
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toBodilessEntity());
    }

    /** O catálogo padrão da loja (contexto DEFAULT, ou o primeiro). */
    public String defaultCatalogId(String merchantId) {
        JsonNode catalogs = call(() -> http.get().uri(properties.catalogsPath(), merchantId).headers(this::auth)
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class));
        if (catalogs == null || !catalogs.isArray() || catalogs.isEmpty()) {
            throw new IfoodApiException(404, "A loja não tem cardápio no iFood.");
        }
        JsonNode chosen = catalogs.get(0);
        for (JsonNode catalog : catalogs) {
            for (JsonNode context : catalog.path("context")) {
                if ("DEFAULT".equals(context.asString())) {
                    chosen = catalog;
                }
            }
        }
        return chosen.path("catalogId").asString(chosen.path("id").asString());
    }

    /** Cria a categoria no catálogo do iFood e devolve o id dela. */
    public String createCategory(String merchantId, String catalogId, String name, String externalCode) {
        JsonNode body = call(() -> http.post()
                .uri("/catalog/v2.0/merchants/{merchantId}/catalogs/{catalogId}/categories", merchantId, catalogId)
                .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "status", "AVAILABLE", "template", "DEFAULT", "externalCode", externalCode))
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class));
        if (body == null || body.path("id").asString("").isBlank()) {
            throw new IfoodApiException(502, "o iFood não devolveu o id da categoria");
        }
        return body.path("id").asString();
    }

    /** Cria ou atualiza um item completo (produto, preço, complementos) numa chamada. */
    public void putItem(String merchantId, Map<String, Object> item) {
        call(() -> http.put().uri("/catalog/v2.0/merchants/{merchantId}/items", merchantId).headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON).body(item)
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toBodilessEntity());
    }

    /** Envia a foto do produto; o iFood devolve o caminho que o item usa. */
    public String uploadImage(String merchantId, byte[] image, String contentType) {
        String data = "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(image);
        JsonNode body = call(() -> http.post().uri("/catalog/v2.0/merchants/{merchantId}/image/upload", merchantId)
                .headers(this::auth).contentType(MediaType.APPLICATION_JSON).body(Map.of("image", data))
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class));
        return body == null ? null : body.path("path").asString(null);
    }

    /**
     * Categorias do cardápio da loja no iFood, com os itens. Usa o catálogo de contexto DEFAULT (ou o primeiro, se a
     * loja só tiver outro).
     */
    public JsonNode catalogCategories(String merchantId) {
        String catalogId = defaultCatalogId(merchantId);
        return call(() -> http.get().uri(properties.categoriesPath(), merchantId, catalogId).headers(this::auth)
                .retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).body(JsonNode.class));
    }

    /** Responde à disputa (pedido de cancelamento do cliente): aceitar, ou recusar com o motivo. */
    public void answerDispute(String disputeId, boolean accept, String reason) {
        call(() -> {
            var request = http.post().uri("/order/v1.0/disputes/{id}/{answer}", disputeId, accept ? "accept" : "reject")
                    .headers(this::auth);
            if (!accept) {
                request = request.contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", reason == null ? "Recusado pela loja" : reason));
            }
            return request.retrieve().onStatus(HttpStatusCode::isError, IfoodClient::fail).toBodilessEntity();
        });
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
