package com.pedeai.store.service;

import com.pedeai.shared.exception.InvalidCredentialsException;
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

    private final AppUserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    /** Comparado quando o e-mail não existe, para o tempo de resposta não revelar quem tem conta. */
    private final String unknownUserPasswordHash;

    public AuthService(AppUserRepository userRepository, StoreRepository storeRepository,
                       PasswordEncoder passwordEncoder, AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.unknownUserPasswordHash = passwordEncoder.encode("usuario-inexistente");
    }

    @Transactional
    public AuthResult login(LoginRequest request, String deviceName) {
        Optional<AppUser> found = userRepository.findByEmail(Emails.normalize(request.email()));
        String passwordHash = found.map(AppUser::getPasswordHash).orElse(unknownUserPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        AppUser user = found
                .filter(candidate -> passwordMatches && candidate.isActive())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_LOGIN));
        return startSession(user, findStore(user), deviceName);
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
