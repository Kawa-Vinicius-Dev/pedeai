package com.pedeai.integration.service;

import com.pedeai.integration.domain.InboundEvent;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.repository.InboundEventRepository;
import com.pedeai.integration.repository.OutboundActionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * O payload cru dos eventos fica 30 dias, para suporte e auditoria (docs/05-integracoes.md#entrada-eventos). O
 * histórico de sincronização de cada pedido fica 90 dias. O que ainda está pendente nunca é apagado.
 */
@Service
public class IntegrationCleanup {
    static final Duration KEEP_EVENTS = Duration.ofDays(30);
    static final Duration KEEP_ACTIONS = Duration.ofDays(90);
    private static final Logger log = LoggerFactory.getLogger(IntegrationCleanup.class);

    private final InboundEventRepository events;
    private final OutboundActionRepository actions;
    private final Clock clock;

    public IntegrationCleanup(InboundEventRepository events, OutboundActionRepository actions, Clock clock) {
        this.events = events;
        this.actions = actions;
        this.clock = clock;
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public int cleanUp() {
        Instant now = Instant.now(clock);
        int deletedEvents = events.deleteHandledBefore(now.minus(KEEP_EVENTS), InboundEvent.Status.PENDING);
        int deletedActions = actions.deleteHandledBefore(now.minus(KEEP_ACTIONS), OutboundAction.Status.PENDING);
        log.info("Limpeza: {} eventos e {} ações de marketplace apagados.", deletedEvents, deletedActions);
        return deletedEvents + deletedActions;
    }
}
