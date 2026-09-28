package com.pedeai.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conta tentativas erradas por chave (e-mail, IP) numa janela de tempo. Passou do limite, bloqueia até as tentativas
 * antigas saírem da janela. Barra quem fica testando senha ou código de pareamento.
 */
// ponytail: em memória, por instância da API; com mais de uma instância, mover para o banco ou para o proxy.
public final class AttemptLimiter {
    /** Acima disso, limpa as chaves sem tentativa recente, para a memória não crescer para sempre. */
    private static final int CLEANUP_ABOVE = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public boolean blocked(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return false;
        }
        synchronized (attempts) {
            prune(attempts, Instant.now(clock));
            return attempts.size() >= maxFailures;
        }
    }

    public void failed(String key) {
        Instant now = Instant.now(clock);
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
        if (failures.size() > CLEANUP_ABOVE) {
            failures.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    prune(entry.getValue(), now);
                    return entry.getValue().isEmpty();
                }
            });
        }
    }

    /** Acertou: as tentativas erradas anteriores deixam de contar. */
    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        while (!attempts.isEmpty() && !attempts.peekFirst().plus(window).isAfter(now)) {
            attempts.pollFirst();
        }
    }
}
