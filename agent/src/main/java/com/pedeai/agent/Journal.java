package com.pedeai.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diário local: "recebido" antes de mandar para a impressora, "impresso" depois (docs/04-impressao.md#evitando-
 * impressão-duplicada). Com ele, o agente nunca imprime a mesma chave de entrega duas vezes, e sabe quando caiu no
 * meio de uma impressão. Uma linha por evento, gravada no disco antes de seguir; guarda os últimos 7 dias.
 */
final class Journal {
    static final Duration RETENTION = Duration.ofDays(7);

    /** FAILED: o envio deu erro e o trabalho volta para a fila; pode tentar de novo. PRINTED não volta atrás. */
    enum State {
        RECEIVED, PRINTED, FAILED
    }

    private final Path file;
    private final Map<String, State> states = new HashMap<>();

    Journal(Path file, Instant now) throws IOException {
        this.file = file;
        Files.createDirectories(file.toAbsolutePath().getParent());
        List<String> kept = new ArrayList<>();
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split(" ");
                if (parts.length != 3) {
                    continue; // linha cortada por queda de energia no meio da gravação
                }
                if (Instant.parse(parts[0]).plus(RETENTION).isBefore(now)) {
                    continue;
                }
                states.merge(parts[2], State.valueOf(parts[1]), Journal::next);
                kept.add(line);
            }
        }
        // Reescreve só com o que ainda vale: o arquivo não cresce para sempre.
        Files.write(file, kept, StandardCharsets.UTF_8);
    }

    State state(String deliveryKey) {
        return states.get(deliveryKey);
    }

    void received(String deliveryKey, Instant now) throws IOException {
        append(State.RECEIVED, deliveryKey, now);
    }

    void printed(String deliveryKey, Instant now) throws IOException {
        append(State.PRINTED, deliveryKey, now);
    }

    void failed(String deliveryKey, Instant now) throws IOException {
        append(State.FAILED, deliveryKey, now);
    }

    private void append(State state, String deliveryKey, Instant now) throws IOException {
        Files.writeString(file, now + " " + state + " " + deliveryKey + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        states.merge(deliveryKey, state, Journal::next);
    }

    private static State next(State old, State current) {
        return old == State.PRINTED ? old : current;
    }
}
