package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import com.pedeai.integration.service.IntegrationCleanup;
import com.pedeai.printing.service.PrintingCleanup;
import com.pedeai.store.service.SessionCleanup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Limpeza diária: apaga o que é velho e já foi resolvido, e nunca o que ainda está pendente. Banco de verdade. */
@SpringBootTest
@AutoConfigureMockMvc
class RetentionIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionCleanup sessions;
    @Autowired
    private PrintingCleanup printing;
    @Autowired
    private IntegrationCleanup integration;

    @Test
    void deletesOldResolvedRowsAndKeepsWhatIsStillOpen() throws Exception {
        String body = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Loja limpeza","ownerName":"Ana","email":"limpeza-%s@example.com",
                         "password":"senha-forte-1"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID store = UUID.fromString(JsonPath.read(body, "$.store.id"));
        UUID user = UUID.fromString(JsonPath.read(body, "$.user.id"));
        Instant now = Instant.now();
        Timestamp old = Timestamp.from(now.minus(Duration.ofDays(120)));
        Timestamp recent = Timestamp.from(now.minus(Duration.ofHours(1)));
        Timestamp future = Timestamp.from(now.plus(Duration.ofDays(10)));

        UUID expiredToken = refreshToken(store, user, old, old);
        UUID validToken = refreshToken(store, user, recent, future);

        UUID expiredCode = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_pairing_code (id, store_id, code_hash, expires_at, created_at, version) "
                + "VALUES (?, ?, ?, ?, ?, 0)", expiredCode, store, "h" + expiredCode, old, old);

        UUID agent = UUID.randomUUID();
        jdbc.update("INSERT INTO print_agent (id, store_id, name, token_hash, created_at, version) "
                + "VALUES (?, ?, 'PC', ?, ?, 0)", agent, store, "t" + agent, old);
        UUID printer = UUID.randomUUID();
        jdbc.update("INSERT INTO printer (id, store_id, agent_id, name, connection_type, host, port, paper_width_mm, "
                + "columns, codepage, cut_mode, active, status, created_at, updated_at, version) VALUES "
                + "(?, ?, ?, 'Cozinha', 'NETWORK', '10.0.0.5', 9100, 80, 48, 'PC860', 'PARTIAL', TRUE, 'ONLINE', ?, ?, 0)",
                printer, store, agent, old, old);
        UUID oldPrinted = printJob(store, printer, agent, "PRINTED", old);
        UUID oldPending = printJob(store, printer, agent, "PENDING", old);
        UUID recentPrinted = printJob(store, printer, agent, "PRINTED", recent);

        UUID oldProcessed = inboundEvent("PROCESSED", old);
        UUID oldPendingEvent = inboundEvent("PENDING", old);
        UUID recentProcessed = inboundEvent("PROCESSED", recent);

        sessions.cleanUp();
        printing.cleanUp();
        integration.cleanUp();

        assertThat(exists("refresh_token", expiredToken)).isFalse();
        assertThat(exists("refresh_token", validToken)).isTrue();
        assertThat(exists("agent_pairing_code", expiredCode)).isFalse();
        assertThat(exists("print_job", oldPrinted)).isFalse();
        assertThat(exists("print_job", oldPending)).isTrue();
        assertThat(exists("print_job", recentPrinted)).isTrue();
        assertThat(exists("inbound_event", oldProcessed)).isFalse();
        assertThat(exists("inbound_event", oldPendingEvent)).isTrue();
        assertThat(exists("inbound_event", recentProcessed)).isTrue();
    }

    private UUID refreshToken(UUID store, UUID user, Timestamp created, Timestamp expires) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO refresh_token (id, store_id, user_id, token_hash, created_at, expires_at, version) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", id, store, user, "r" + id.toString().replace("-", ""), created, expires);
        return id;
    }

    private UUID printJob(UUID store, UUID printer, UUID agent, String status, Timestamp created) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO print_job (id, store_id, printer_id, agent_id, document_type, reason, idempotency_key, "
                + "delivery_key, status, attempts, next_attempt_at, expires_at, payload, preview, created_at, title, "
                + "version) VALUES (?, ?, ?, ?, 'PRODUCTION_TICKET', 'AUTO', ?, ?, ?, 0, ?, ?, ?, 'x', ?, 'Pedido', 0)",
                id, store, printer, agent, "k" + id, "d" + id, status, created, created, new byte[]{1}, created);
        return id;
    }

    private UUID inboundEvent(String status, Timestamp received) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inbound_event (id, provider, external_event_id, event_code, payload, status, attempts, "
                + "next_attempt_at, received_at, version) VALUES (?, 'IFOOD', ?, 'PLC', '{}', ?, 0, ?, ?, 0)",
                id, "e" + id, status, received, received);
        return id;
    }

    private boolean exists(String table, UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id) == 1;
    }
}
