package com.pedeai.printing.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintJobTest {
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    private static PrintJob job() {
        return new PrintJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DocumentType.PRODUCTION_TICKET,
                UUID.randomUUID(), UUID.randomUUID(), PrintJob.Reason.AUTO, "chave", "Pedido 1 · Cozinha", new byte[]{1}, "texto",
                Duration.ofMinutes(20), NOW);
    }

    @Test
    void onlyOneReservationWinsAndPrintingNeedsIt() {
        PrintJob job = job();
        assertThatThrownBy(() -> job.printed(NOW)).isInstanceOf(IllegalStateException.class);

        assertThat(job.reserve(NOW)).isTrue();
        assertThat(job.reserve(NOW)).isFalse();
        job.printed(NOW.plusSeconds(2));

        assertThat(job.getStatus()).isEqualTo(PrintJob.Status.PRINTED);
        assertThat(job.getPrintedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void errorsRetryWithGrowingWaitAndFailOnTheFifthAttempt() {
        PrintJob job = job();
        Instant now = NOW;
        Duration[] waits = {Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(30), Duration.ofMinutes(1)};
        for (Duration wait : waits) {
            assertThat(job.reserve(now)).isTrue();
            job.failed("Conexão recusada", now);
            assertThat(job.getStatus()).isEqualTo(PrintJob.Status.PENDING);
            assertThat(job.getNextAttemptAt()).isEqualTo(now.plus(wait));
            // Antes da espera acabar, o agente não consegue reservar de novo.
            assertThat(job.reserve(now.plus(wait).minusSeconds(1))).isFalse();
            now = now.plus(wait);
        }
        assertThat(job.reserve(now)).isTrue();
        job.failed("Conexão recusada", now);

        assertThat(job.getStatus()).isEqualTo(PrintJob.Status.FAILED);
        assertThat(job.getAttempts()).isEqualTo(PrintJob.MAX_ATTEMPTS);
        assertThat(job.getLastError()).isEqualTo("Conexão recusada");
    }

    @Test
    void reservationWithoutAnswerGoesBackToTheQueue() {
        PrintJob job = job();
        job.reserve(NOW);

        assertThat(job.releaseExpiredLease(NOW.plus(PrintJob.LEASE).minusSeconds(1))).isFalse();
        assertThat(job.releaseExpiredLease(NOW.plus(PrintJob.LEASE).plusSeconds(1))).isTrue();
        assertThat(job.getStatus()).isEqualTo(PrintJob.Status.PENDING);
        assertThat(job.getAttempts()).isEqualTo(1);
    }

    @Test
    void staleJobsExpireAndPendingOnesCanBeCancelled() {
        PrintJob stale = job();
        assertThat(stale.expireIfStale(NOW.plus(Duration.ofMinutes(19)))).isFalse();
        assertThat(stale.expireIfStale(NOW.plus(Duration.ofMinutes(21)))).isTrue();
        assertThat(stale.getStatus()).isEqualTo(PrintJob.Status.EXPIRED);

        PrintJob sent = job();
        sent.reserve(NOW);
        assertThat(sent.cancelIfPending()).isFalse();
        PrintJob pending = job();
        assertThat(pending.cancelIfPending()).isTrue();
        assertThat(pending.getStatus()).isEqualTo(PrintJob.Status.CANCELLED);
    }

    @Test
    void uncertainWaitsForAPerson() {
        PrintJob job = job();
        job.reserve(NOW);
        job.uncertain("Agente reiniciou no meio do envio", NOW);

        assertThat(job.getStatus()).isEqualTo(PrintJob.Status.UNCERTAIN);
        assertThat(job.reserve(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    void everyJobHasItsOwnDeliveryKey() {
        assertThat(job().getDeliveryKey()).isNotEqualTo(job().getDeliveryKey());
    }
}
