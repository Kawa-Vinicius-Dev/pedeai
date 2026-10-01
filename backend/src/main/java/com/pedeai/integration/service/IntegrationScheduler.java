package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.InboundEvent;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.repository.InboundEventRepository;
import com.pedeai.integration.repository.MarketplaceConnectionRepository;
import com.pedeai.order.domain.OrderSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Os três laços da integração (docs/05-integracoes.md#regras-da-plataforma-que-o-pedeaí-precisa-cumprir): o polling
 * a cada 30 s (é ele que mantém a loja online no iFood), o processamento do inbox e o envio do outbox.
 */
@Component
public class IntegrationScheduler {
    static final int POLLING_BATCH = 100;
    private static final int BATCH = 50;
    private static final Logger log = LoggerFactory.getLogger(IntegrationScheduler.class);

    private final IfoodProperties properties;
    private final IfoodClient ifood;
    private final MarketplaceConnectionRepository connections;
    private final InboundEventRepository events;
    private final InboundService inbound;
    private final InboundEventHandler handler;
    private final OutboxService outbox;
    private final Clock clock;

    public IntegrationScheduler(IfoodProperties properties, IfoodClient ifood,
                                MarketplaceConnectionRepository connections, InboundEventRepository events,
                                InboundService inbound, InboundEventHandler handler, OutboxService outbox,
                                Clock clock) {
        this.properties = properties;
        this.ifood = ifood;
        this.connections = connections;
        this.events = events;
        this.inbound = inbound;
        this.handler = handler;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Lotes de até 100 merchants. O ack sai só depois de gravar; se falhar, o evento volta e o inbox deduplica. */
    // ponytail: synchronized serializa os laços numa instância; com mais de uma instância da API, usar ShedLock.
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public synchronized void poll() {
        if (!properties.configured()) {
            return;
        }
        List<String> merchants = connections.findAllByProviderAndStatus(OrderSource.IFOOD,
                        MarketplaceConnection.Status.ACTIVE).stream()
                .map(MarketplaceConnection::getExternalMerchantId).toList();
        for (int start = 0; start < merchants.size(); start += POLLING_BATCH) {
            List<String> batch = merchants.subList(start, Math.min(merchants.size(), start + POLLING_BATCH));
            try {
                List<String> received = new ArrayList<>();
                for (JsonNode event : ifood.poll(batch)) {
                    inbound.record(OrderSource.IFOOD, event);
                    received.add(event.path("id").asString());
                }
                ifood.acknowledge(received);
            } catch (IfoodClient.IfoodApiException e) {
                // 429 ou fora do ar: o próximo ciclo tenta de novo, no intervalo fixo que o iFood pede.
                log.warn("Polling do iFood falhou para {} merchants: {}", batch.size(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 2_000)
    public synchronized void processInbox() {
        List<UUID> due = events.findAllByStatusAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                        InboundEvent.Status.PENDING, Instant.now(clock), PageRequest.of(0, BATCH)).stream()
                .map(InboundEvent::getId).toList();
        for (UUID eventId : due) {
            try {
                handler.handle(eventId);
            } catch (RuntimeException e) {
                log.warn("Evento {} do marketplace vai ser tentado de novo: {}", eventId, e.getMessage());
                handler.retryLater(eventId, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 2_000)
    public synchronized void sendOutbox() {
        for (UUID actionId : outbox.due(BATCH)) {
            try {
                outbox.send(actionId);
            } catch (RuntimeException e) {
                log.warn("Ação {} para o marketplace falhou: {}", actionId, e.getMessage());
            }
        }
    }
}
