package com.pedeai.agent;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** As chamadas do agente à API (docs/04-impressao.md#protocolo-agente--servidor). */
final class ApiClient {
    static final String VERSION = "0.2.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    record Pairing(UUID agentId, String agentName, String storeName, String token) {
    }

    record PrinterConfig(UUID id, String name, String connectionType, String host, Integer port, String systemName) {
        boolean network() {
            return "NETWORK".equals(connectionType);
        }
    }

    /** {@code latestVersion}: a versão mais nova do agente, para avisar quando este computador está atrás. */
    record Config(UUID agentId, String agentName, List<PrinterConfig> printers, String latestVersion,
                  String downloadUrl) {
    }

    record Job(UUID id, UUID printerId, String deliveryKey, String documentType, byte[] payload) {
    }

    record PrinterStatus(UUID printerId, String status, String detail) {
    }

    /** Resposta com erro da API. 401: o computador foi removido na tela e o token não vale mais. */
    static final class ApiException extends IOException {
        final int status;

        ApiException(int status, String body) {
            super("A API respondeu " + status + ": " + body);
            this.status = status;
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;
    private final String token;

    ApiClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    static Pairing pair(String baseUrl, String code, String name, String os) throws IOException, InterruptedException {
        record Body(String code, String name, String os, String agentVersion) {
        }
        return new ApiClient(baseUrl, null).send("POST", "/api/agent/pairings", new Body(code, name, os, VERSION),
                Pairing.class);
    }

    Config config() throws IOException, InterruptedException {
        return send("GET", "/api/agent/config", null, Config.class);
    }

    List<Job> jobs() throws IOException, InterruptedException {
        return List.of(send("GET", "/api/agent/jobs", null, Job[].class));
    }

    /** SENT, PRINTED, FAILED ou UNCERTAIN. SENT responde 409 se o trabalho não está mais na fila. */
    void update(UUID jobId, String status, String error) throws IOException, InterruptedException {
        record Body(String status, String error) {
        }
        send("PATCH", "/api/agent/jobs/" + jobId, new Body(status, truncate(error)), Void.class);
    }

    void heartbeat(List<PrinterStatus> printers) throws IOException, InterruptedException {
        record Body(String agentVersion, List<PrinterStatus> printers) {
        }
        send("PUT", "/api/agent/status", new Body(VERSION, printers), Void.class);
    }

    private <T> T send(String method, String path, Object body, Class<T> type)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 300) {
            throw new ApiException(response.statusCode(), new String(response.body()));
        }
        return type == Void.class || response.body().length == 0 ? null : JSON.readValue(response.body(), type);
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 255 ? text : text.substring(0, 255);
    }
}
