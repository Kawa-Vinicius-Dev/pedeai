package com.pedeai.printing.service;

import com.pedeai.printing.dto.AgentPairingRequest;
import com.pedeai.printing.repository.AgentPairingCodeRepository;
import com.pedeai.printing.repository.PrintAgentRepository;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.store.service.StoreService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTest {
    private static final AgentPairingRequest WRONG = new AgentPairingRequest("123456", "Caixa", null, null);

    @Test
    void blocksGuessingThePairingCodeAfterTooManyFailures() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-26T12:00:00Z"));
        AgentPairingCodeRepository codes = mock(AgentPairingCodeRepository.class);
        when(codes.findAllByCodeHash(anyString())).thenReturn(List.of());
        AgentService service = new AgentService(mock(PrintAgentRepository.class), codes,
                mock(PrinterRepository.class), mock(StoreService.class), clock);

        for (int attempt = 0; attempt < AgentService.MAX_FAILED_PAIRINGS; attempt++) {
            assertThatThrownBy(() -> service.pair(WRONG, "10.0.0.1")).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> service.pair(WRONG, "10.0.0.1"))
                .isInstanceOf(com.pedeai.shared.exception.TooManyRequestsException.class)
                .hasMessage(AgentService.TOO_MANY_ATTEMPTS);
        // Outro endereço não é afetado, e o bloqueio acaba com o tempo.
        assertThatThrownBy(() -> service.pair(WRONG, "10.0.0.2")).isInstanceOf(InvalidCredentialsException.class);
        clock.now = clock.now.plus(AgentService.FAILED_PAIRING_WINDOW).plusSeconds(1);
        assertThatThrownBy(() -> service.pair(WRONG, "10.0.0.1")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void forgedAddressesStillHitTheGlobalCap() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-26T12:00:00Z"));
        AgentPairingCodeRepository codes = mock(AgentPairingCodeRepository.class);
        when(codes.findAllByCodeHash(anyString())).thenReturn(List.of());
        AgentService service = new AgentService(mock(PrintAgentRepository.class), codes,
                mock(PrinterRepository.class), mock(StoreService.class), clock);

        for (int attempt = 0; attempt < AgentService.MAX_FAILED_PAIRINGS_GLOBAL; attempt++) {
            String forged = "10.1." + (attempt / 250) + "." + (attempt % 250);
            assertThatThrownBy(() -> service.pair(WRONG, forged)).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> service.pair(WRONG, "10.9.9.9"))
                .isInstanceOf(com.pedeai.shared.exception.TooManyRequestsException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
