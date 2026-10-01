package com.pedeai.store.service;

import com.pedeai.store.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Cada tela renova a sessão a cada 15 minutos: sem limpeza, a tabela de refresh tokens cresce sem parar. */
@Service
public class SessionCleanup {
    static final Duration KEEP_AFTER_EXPIRY = Duration.ofDays(1);
    private static final Logger log = LoggerFactory.getLogger(SessionCleanup.class);

    private final RefreshTokenRepository repository;
    private final Clock clock;

    public SessionCleanup(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Todo dia às 4h30, fora do horário de pico. */
    @Scheduled(cron = "0 30 4 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public int cleanUp() {
        int deleted = repository.deleteExpiredBefore(Instant.now(clock).minus(KEEP_AFTER_EXPIRY));
        log.info("Limpeza: {} refresh tokens vencidos apagados.", deleted);
        return deleted;
    }
}
