package com.pedeai.realtime.service;

import com.pedeai.realtime.dto.RealtimeEvent;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conexões SSE abertas, por loja, em memória. Com mais de uma instância da API, ganha uma implementação com
 * LISTEN/NOTIFY do PostgreSQL (docs/02-arquitetura.md).
 */
@Service
public class RealtimeBroadcaster {
    /** A tela reconecta sozinha; reconectar de tempos em tempos também renova o token de acesso. */
    static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, Set<SseEmitter>> emittersByStore = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID storeId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> emitters = emittersByStore.computeIfAbsent(storeId, id -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        // Primeira mensagem: a tela sabe que conectou e o proxy não segura a resposta esperando dados.
        send(emitters, emitter, SseEmitter.event().comment("conectado"));
        return emitter;
    }

    public void publish(UUID storeId, RealtimeEvent event) {
        Set<SseEmitter> emitters = emittersByStore.get(storeId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitters, emitter, SseEmitter.event().name(event.type()).data(event, MediaType.APPLICATION_JSON));
        }
    }

    /** Comentário a cada 25 s para proxies não fecharem a conexão parada. */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void keepAlive() {
        emittersByStore.values().forEach(emitters ->
                emitters.forEach(emitter -> send(emitters, emitter, SseEmitter.event().comment("keepalive"))));
    }

    int connections(UUID storeId) {
        Set<SseEmitter> emitters = emittersByStore.get(storeId);
        return emitters == null ? 0 : emitters.size();
    }

    private static void send(Set<SseEmitter> emitters, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException closed) {
            // A tela fechou ou a conexão caiu: ela reconecta sozinha.
            emitters.remove(emitter);
        }
    }
}
