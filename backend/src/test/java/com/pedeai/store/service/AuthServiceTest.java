package com.pedeai.store.service;

import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.shared.security.AccessTokenService;
import com.pedeai.shared.security.AccessTokenService.IssuedAccessToken;
import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.RefreshToken;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.LoginRequest;
import com.pedeai.store.repository.AppUserRepository;
import com.pedeai.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static com.pedeai.support.TestSecurity.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private AccessTokenService accessTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService service;
    private Store store;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, storeRepository, passwordEncoder, accessTokenService,
                refreshTokenService);
        store = new Store("Pizzaria Bella", NOW);
        owner = new AppUser(store.getId(), "Ana", "ana@example.com", passwordEncoder.encode("senha-forte-1"),
                Role.OWNER, NOW);
    }

    @Test
    void loginOpensSessionForValidCredentials() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(owner));
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        stubTokens();

        AuthResult result = service.login(new LoginRequest("  Ana@Example.COM ", "senha-forte-1"), "Chrome");

        assertThat(result.response().accessToken()).isEqualTo("jwt");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(900);
        assertThat(result.response().user().email()).isEqualTo("ana@example.com");
        assertThat(result.response().store().name()).isEqualTo("Pizzaria Bella");
        assertThat(result.refreshToken().value()).isEqualTo("refresh");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@example.com", "errada-123"), null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(AuthService.INVALID_LOGIN);
        verify(refreshTokenService, never()).issue(any(), any());
    }

    @Test
    void loginRejectsUnknownEmailWithTheSameMessage() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ninguem@example.com", "qualquer-1"), null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(AuthService.INVALID_LOGIN);
    }

    @Test
    void loginRejectsInactiveUser() {
        owner.changeActive(false, NOW);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@example.com", "senha-forte-1"), null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshConsumesTokenAndOpensNewSession() {
        RefreshToken consumed = new RefreshToken(store.getId(), owner.getId(), "hash", null, NOW,
                NOW.plus(Duration.ofDays(1)));
        when(refreshTokenService.consume("antigo")).thenReturn(consumed);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        stubTokens();

        AuthResult result = service.refresh("antigo", "Chrome");

        assertThat(result.refreshToken().value()).isEqualTo("refresh");
        verify(refreshTokenService).issue(owner, "Chrome");
    }

    @Test
    void refreshRejectsDeactivatedUser() {
        owner.changeActive(false, NOW);
        RefreshToken consumed = new RefreshToken(store.getId(), owner.getId(), "hash", null, NOW,
                NOW.plus(Duration.ofDays(1)));
        when(refreshTokenService.consume("antigo")).thenReturn(consumed);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.refresh("antigo", null)).isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).issue(any(), any());
    }

    @Test
    void refreshPropagatesInvalidToken() {
        when(refreshTokenService.consume(null)).thenThrow(new InvalidCredentialsException("expirou"));

        assertThatThrownBy(() -> service.refresh(null, null)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logoutRevokesTheToken() {
        service.logout("valor");

        verify(refreshTokenService).revoke("valor");
    }

    private void stubTokens() {
        when(accessTokenService.issue(owner.getId(), store.getId(), Role.OWNER, "Ana"))
                .thenReturn(new IssuedAccessToken("jwt", 900));
        when(refreshTokenService.issue(any(), any()))
                .thenReturn(new IssuedRefreshToken("refresh", NOW.plus(Duration.ofDays(30))));
    }
}
