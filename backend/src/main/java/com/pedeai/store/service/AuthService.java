package com.pedeai.store.service;

import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.shared.exception.TooManyRequestsException;
import com.pedeai.shared.security.AttemptLimiter;
import com.pedeai.shared.security.AccessTokenService;
import com.pedeai.shared.security.AccessTokenService.IssuedAccessToken;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.RefreshToken;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.AuthResponse;
import com.pedeai.store.dto.LoginRequest;
import com.pedeai.store.dto.StoreSummaryResponse;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.repository.AppUserRepository;
import com.pedeai.store.repository.StoreRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {
    static final String INVALID_LOGIN = "E-mail ou senha inválidos.";
    static final String TOO_MANY_ATTEMPTS = "Muitas tentativas de login erradas. Espere 15 minutos e tente de novo.";
    /** Por e-mail: barra quem testa senhas numa conta. Por IP: barra quem testa muitas contas de uma vez. */
    static final int MAX_FAILURES_PER_EMAIL = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    static final java.time.Duration FAILURE_WINDOW = java.time.Duration.ofMinutes(15);

    private final AppUserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    /** Comparado quando o e-mail não existe, para o tempo de resposta não revelar quem tem conta. */
    private final String unknownUserPasswordHash;
    private final AttemptLimiter byEmail;
    private final AttemptLimiter byIp;

    public AuthService(AppUserRepository userRepository, StoreRepository storeRepository,
                       PasswordEncoder passwordEncoder, AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService, java.time.Clock clock) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.unknownUserPasswordHash = passwordEncoder.encode("usuario-inexistente");
        this.byEmail = new AttemptLimiter(MAX_FAILURES_PER_EMAIL, FAILURE_WINDOW, clock);
        this.byIp = new AttemptLimiter(MAX_FAILURES_PER_IP, FAILURE_WINDOW, clock);
    }

    /** {@code clientKey}: de onde veio a tentativa (o IP). Tentativas erradas demais bloqueiam por 15 minutos. */
    @Transactional
    public AuthResult login(LoginRequest request, String deviceName, String clientKey) {
        String email = Emails.normalize(request.email());
        String emailKey = "email:" + email;
        String ipKey = "ip:" + clientKey;
        if (byEmail.blocked(emailKey) || byIp.blocked(ipKey)) {
            throw new TooManyRequestsException(TOO_MANY_ATTEMPTS);
        }
        Optional<AppUser> found = userRepository.findByEmail(email);
        String passwordHash = found.map(AppUser::getPasswordHash).orElse(unknownUserPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        Optional<AppUser> user = found.filter(candidate -> passwordMatches && candidate.isActive());
        if (user.isEmpty()) {
            byEmail.failed(emailKey);
            byIp.failed(ipKey);
            throw new InvalidCredentialsException(INVALID_LOGIN);
        }
        byEmail.reset(emailKey);
        return startSession(user.get(), findStore(user.get()), deviceName);
    }

    /** Não desfaz a transação no 401: a revocação por reuso de token precisa ficar gravada. */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResult refresh(String refreshToken, String deviceName) {
        RefreshToken consumed = refreshTokenService.consume(refreshToken);
        AppUser user = userRepository.findById(consumed.getUserId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new InvalidCredentialsException(RefreshTokenService.SESSION_EXPIRED));
        return startSession(user, findStore(user), deviceName);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    AuthResult startSession(AppUser user, Store store, String deviceName) {
        IssuedAccessToken accessToken = accessTokenService.issue(user.getId(), store.getId(), user.getRole(),
                user.getName());
        IssuedRefreshToken refreshToken = refreshTokenService.issue(user, deviceName);
        AuthResponse response = new AuthResponse(accessToken.value(), "Bearer", accessToken.expiresInSeconds(),
                UserResponse.from(user), StoreSummaryResponse.from(store));
        return new AuthResult(response, refreshToken);
    }

    private Store findStore(AppUser user) {
        return storeRepository.findById(user.getStoreId())
                .orElseThrow(() -> new IllegalStateException("Usuário sem loja: " + user.getId()));
    }
}
