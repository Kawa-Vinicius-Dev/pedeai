package com.pedeai.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O agente contra uma API falsa (HttpServer do JDK) e uma impressora de rede falsa (socket local). */
class AgentRunnerTest {
    private static final String JOB_ID = "01a0d567-0000-7000-8000-0000000000d1";
    private static final String PRINTER_ID = "01a0d567-0000-7000-8000-0000000000d2";
    private static final byte[] TICKET = "PEDIDO 42\n".getBytes(StandardCharsets.US_ASCII);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private HttpServer api;
    private FakePrinter printer;
    private final List<String> patches = new CopyOnWriteArrayList<>();
    private volatile String jobsJson;
    private volatile int reserveStatus = 204;

    @BeforeEach
    void start() throws IOException {
        printer = new FakePrinter();
        jobsJson = job("chave-1");
        api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/api/agent/config", exchange -> respond(exchange, 200, """
                {"agentId":"01a0d567-0000-7000-8000-0000000000d0","agentName":"Caixa","printers":[
                  {"id":"%s","name":"Cozinha","connectionType":"NETWORK","host":"127.0.0.1","port":%d}]}"""
                .formatted(PRINTER_ID, printer.port())));
        api.createContext("/api/agent/jobs", exchange -> {
            if (exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 200, jobsJson);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String status = body.replaceAll(".*\"status\":\"(\\w+)\".*", "$1");
            patches.add(status);
            respond(exchange, status.equals("SENT") ? reserveStatus : 204, "");
        });
        api.start();
    }

    @AfterEach
    void stop() throws IOException {
        api.stop(0);
        printer.close();
    }

    @Test
    void printsOnceAndNeverAgainForTheSameDeliveryKey() throws Exception {
        AgentRunner runner = runner(new Journal(folder.resolve("diario.log"), CLOCK.instant()));
        runner.refreshConfig();

        assertEquals(1, runner.processJobs());
        assertArrayEquals(TICKET, printer.received());
        assertEquals(List.of("SENT", "PRINTED"), patches);

        // A confirmação se perdeu e o servidor devolveu o trabalho para a fila: confirma sem imprimir de novo.
        patches.clear();
        AgentRunner restarted = runner(new Journal(folder.resolve("diario.log"), CLOCK.instant()));
        restarted.refreshConfig();
        assertEquals(0, restarted.processJobs());
        assertEquals(List.of("SENT", "PRINTED"), patches);
        assertEquals(1, printer.connections());
    }

    @Test
    void crashBetweenReceivedAndPrintedBecomesUncertain() throws Exception {
        Journal journal = new Journal(folder.resolve("diario.log"), CLOCK.instant());
        journal.received("chave-1", CLOCK.instant());

        AgentRunner runner = runner(new Journal(folder.resolve("diario.log"), CLOCK.instant()));
        runner.refreshConfig();

        assertEquals(0, runner.processJobs());
        assertEquals(List.of("SENT", "UNCERTAIN"), patches);
        assertEquals(0, printer.connections());
    }

    @Test
    void printerOffFailsTheJobAndTheRetryPrints() throws Exception {
        AgentRunner runner = runner(new Journal(folder.resolve("diario.log"), CLOCK.instant()));
        runner.refreshConfig();
        printer.close();

        assertEquals(0, runner.processJobs());
        assertEquals(List.of("SENT", "FAILED"), patches);

        // Impressora de volta (mesma porta) e o trabalho de volta na fila: agora sai.
        printer = new FakePrinter(printer.port());
        patches.clear();
        assertEquals(1, runner.processJobs());
        assertEquals(List.of("SENT", "PRINTED"), patches);
        assertArrayEquals(TICKET, printer.received());
    }

    @Test
    void jobTakenElsewhereIsSkipped() throws Exception {
        reserveStatus = 409;
        AgentRunner runner = runner(new Journal(folder.resolve("diario.log"), CLOCK.instant()));
        runner.refreshConfig();

        assertEquals(0, runner.processJobs());
        assertEquals(List.of("SENT"), patches);
        assertEquals(0, printer.connections());
    }

    @Test
    void journalForgetsAfterSevenDaysAndSurvivesACutLine() throws Exception {
        Path file = folder.resolve("diario.log");
        Journal old = new Journal(file, CLOCK.instant().minus(Journal.RETENTION).minusSeconds(60));
        old.printed("antiga", CLOCK.instant().minus(Journal.RETENTION).minusSeconds(60));
        old.printed("recente", CLOCK.instant());
        java.nio.file.Files.writeString(file, "2026-09-27T12:00:00Z PRIN", java.nio.file.StandardOpenOption.APPEND);

        Journal journal = new Journal(file, CLOCK.instant());

        assertEquals(null, journal.state("antiga"));
        assertEquals(Journal.State.PRINTED, journal.state("recente"));
        assertTrue(java.nio.file.Files.readString(file).contains("recente"));
    }

    private AgentRunner runner(Journal journal) {
        return new AgentRunner(new ApiClient("http://127.0.0.1:" + api.getAddress().getPort(), "token"), journal,
                CLOCK);
    }

    private static String job(String deliveryKey) {
        return """
                [{"id":"%s","printerId":"%s","deliveryKey":"%s","documentType":"PRODUCTION_TICKET","payload":"%s"}]"""
                .formatted(JOB_ID, PRINTER_ID, deliveryKey, Base64.getEncoder().encodeToString(TICKET));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    /** Impressora de rede falsa: aceita conexões e guarda os bytes de cada uma. */
    private static final class FakePrinter implements AutoCloseable {
        private final ServerSocket socket;
        private final List<byte[]> jobs = Collections.synchronizedList(new ArrayList<>());
        private final Thread thread;

        FakePrinter() throws IOException {
            this(0);
        }

        FakePrinter(int port) throws IOException {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            thread = Thread.ofVirtual().start(() -> {
                while (!socket.isClosed()) {
                    try (Socket connection = socket.accept(); InputStream input = connection.getInputStream()) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        input.transferTo(bytes);
                        jobs.add(bytes.toByteArray());
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        int port() {
            return socket.getLocalPort();
        }

        int connections() {
            return jobs.size();
        }

        byte[] received() throws InterruptedException {
            for (int i = 0; i < 50 && jobs.isEmpty(); i++) {
                Thread.sleep(20);
            }
            return jobs.getLast();
        }

        @Override
        public void close() throws IOException {
            socket.close();
            thread.interrupt();
        }
    }
}
