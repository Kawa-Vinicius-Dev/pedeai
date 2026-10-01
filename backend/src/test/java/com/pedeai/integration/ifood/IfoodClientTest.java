package com.pedeai.integration.ifood;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.support.IntegrationTestProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O cliente contra um iFood falso (HttpServer do JDK): token, cabeçalhos, 204, 401 e 5xx. */
class IfoodClientTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger tokens = new AtomicInteger();
    private volatile int pollingStatus = 200;
    private volatile boolean revokeNextCall;
    private IfoodClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/authentication/v1.0/oauth/token", exchange -> {
            String form = URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            requests.add("TOKEN " + form);
            respond(exchange, 200, """
                    {"accessToken":"token-%d","type":"bearer","expiresIn":21600}""".formatted(tokens.incrementAndGet()));
        });
        server.createContext("/order/v1.0/orders:polling", exchange -> {
            requests.add("POLL " + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("x-polling-merchants"));
            if (revokeNextCall) {
                revokeNextCall = false;
                respond(exchange, 401, "{\"message\":\"token revogado\"}");
            } else if (pollingStatus == 204) {
                respond(exchange, 204, "");
            } else {
                respond(exchange, pollingStatus, pollingStatus == 200 ? """
                        [{"id":"evt-1","code":"PLC","orderId":"o-1","merchantId":"m-1"}]""" : "fora do ar");
            }
        });
        server.createContext("/order/v1.0/orders:acknowledgment", exchange -> {
            requests.add("ACK " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 202, "");
        });
        server.createContext("/order/v1.0/orders/o-1/confirm", exchange -> {
            requests.add("CONFIRM " + exchange.getRequestMethod());
            respond(exchange, 202, "");
        });
        server.start();
        client = new IfoodClient(IntegrationTestProperties.ifood(true, "http://127.0.0.1:" + server.getAddress().getPort(),
                "cliente", "segredo", "/order/v1.0/orders:polling", "/order/v1.0/orders:acknowledgment", false),
                CLOCK);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void getsATokenOnceAndPollsWithTheMerchantsHeader() {
        assertThat(client.poll(List.of("m-1", "m-2"))).singleElement()
                .satisfies(event -> assertThat(event.path("code").asString()).isEqualTo("PLC"));
        client.poll(List.of("m-1"));
        client.acknowledge(List.of("evt-1"));
        client.orderAction("o-1", "confirm");

        assertThat(requests).containsExactly(
                "TOKEN grantType=client_credentials&clientId=cliente&clientSecret=segredo",
                "POLL Bearer token-1 m-1,m-2",
                "POLL Bearer token-1 m-1",
                "ACK {\"acknowledgedEventIds\":[\"evt-1\"]}",
                "CONFIRM POST");
    }

    @Test
    void noNewEventsIsAnEmptyList() {
        pollingStatus = 204;

        assertThat(client.poll(List.of("m-1"))).isEmpty();
    }

    @Test
    void revokedTokenIsRenewedOnceAndTheCallRepeated() {
        revokeNextCall = true;

        assertThat(client.poll(List.of("m-1"))).hasSize(1);
        assertThat(tokens.get()).isEqualTo(2);
    }

    @Test
    void serverErrorsCanBeRetriedAndRuleErrorsCannot() {
        pollingStatus = 503;
        assertThatThrownBy(() -> client.poll(List.of("m-1")))
                .isInstanceOfSatisfying(IfoodClient.IfoodApiException.class,
                        error -> assertThat(error.retryable()).isTrue());

        pollingStatus = 400;
        assertThatThrownBy(() -> client.poll(List.of("m-1")))
                .isInstanceOfSatisfying(IfoodClient.IfoodApiException.class,
                        error -> assertThat(error.retryable()).isFalse());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
