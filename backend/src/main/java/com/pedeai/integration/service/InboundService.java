package com.pedeai.integration.service;

import com.pedeai.integration.domain.InboundEvent;
import com.pedeai.integration.repository.InboundEventRepository;
import com.pedeai.order.domain.OrderSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;

/** Grava o evento recebido antes de qualquer processamento. O mesmo evento chegando de novo é gravado uma vez. */
@Service
public class InboundService {
    private final InboundEventRepository repository;
    private final Clock clock;

    public InboundService(InboundEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @return {@code false} se o evento já estava gravado (reenvio da plataforma) */
    @Transactional
    public boolean record(OrderSource provider, JsonNode event) {
        String eventId = event.path("id").asString();
        if (eventId.isBlank() || repository.existsByProviderAndExternalEventId(provider, eventId)) {
            return false;
        }
        String code = event.path("code").asString(event.path("fullCode").asString("?"));
        repository.save(new InboundEvent(provider, eventId, blankToNull(event.path("merchantId").asString()),
                blankToNull(event.path("orderId").asString()), code, event.toString(), Instant.now(clock)));
        return true;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
