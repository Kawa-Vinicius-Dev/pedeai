package com.pedeai.store.service;

import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.RefreshToken;
import com.pedeai.store.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, properties(true), CLOCK);
        user = new AppUser(STORE_ID, "Ana", "ana@example.com", "hash", Role.OWNER, NOW);
    }

    @Test
    void issueStoresOnlyTheHashAndReturnsTheRawValue() {
        IssuedRefreshToken issued = service.issue(user, "Chrome");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(issued.value())
                .isEqualTo(RefreshTokenService.hash(issued.value()));
        assertThat(saved.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(saved.getValue().getStoreId()).isEqualTo(STORE_ID);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void issueTruncatesLongDeviceNames() {
        service.issue(user, "x".repeat(500));

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDeviceName()).hasSize(200);
    }

    @Test
    void consumeRevokesAValidToken() {
        RefreshToken token = token(NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(RefreshTokenService.hash("valor"))).thenReturn(Optional.of(token));

        RefreshToken consumed = service.consume("valor");

        assertThat(consumed.isRevoked()).isTrue();
    }

    @Test
    void consumeRejectsMissingToken() {
        assertThatThrownBy(() -> service.consume(null)).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.consume(" ")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void consumeRejectsUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("desconhecido")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void consumeRejectsExpiredToken() {
        RefreshToken token = token(NOW);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("vencido")).isInstanceOf(InvalidCredentialsException.class);
        verify(repository, never()).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void reusingARevokedTokenRevokesEverySessionOfTheUser() {
        RefreshToken token = token(NOW.plus(Duration.ofDays(1)));
        token.revoke(NOW.minusSeconds(60));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("reusado")).isInstanceOf(InvalidCredentialsException.class);
        verify(repository).revokeAllActiveByUserId(user.getId(), NOW);
    }

    @Test
    void revokeIgnoresMissingCookieAndRevokesKnownToken() {
        service.revoke(null);
        verify(repository, never()).findByTokenHash(any());

        RefreshToken token = token(NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(RefreshTokenService.hash("valor"))).thenReturn(Optional.of(token));
        service.revoke("valor");
        assertThat(token.isRevoked()).isTrue();
    }

    private RefreshToken token(java.time.Instant expiresAt) {
        return new RefreshToken(STORE_ID, user.getId(), "hash", "Chrome", NOW.minusSeconds(3600), expiresAt);
    }
}
