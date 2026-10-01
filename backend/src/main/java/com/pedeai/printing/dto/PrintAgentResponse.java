package com.pedeai.printing.dto;

import com.pedeai.printing.domain.PrintAgent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** {@code online}: mandou heartbeat no último minuto. */
public record PrintAgentResponse(
        UUID id,
        String name,
        @Schema(types = {"string", "null"}) String os,
        @Schema(types = {"string", "null"}) String agentVersion,
        boolean online,
        @Schema(types = {"string", "null"}) Instant lastSeenAt,
        Instant createdAt,
        boolean outdated
) {
    public static PrintAgentResponse from(PrintAgent agent, Instant now, boolean outdated) {
        return new PrintAgentResponse(agent.getId(), agent.getName(), agent.getOs(), agent.getAgentVersion(),
                agent.isOnlineAt(now), agent.getLastSeenAt(), agent.getCreatedAt(), outdated);
    }
}
