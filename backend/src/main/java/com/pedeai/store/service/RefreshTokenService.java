package com.pedeai.store.service;

import com.pedeai.shared.config.AppProperties;
import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.shared.security.SecretTokens;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.RefreshToken;
import com.pedeai.store.domain.RevokeReason;
import com.pedeai.store.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Emite, valida e revoga refresh tokens. Roda dentro da transação de quem chama. */
@Service
public class RefreshTokenService {
    static final String SESSION_EXPIRED = "Sua sessão expirou. Faça login novamente.";
    private static final int MAX_DEVICE_NAME = 200;

    private final RefreshTokenRepository repository;
    private final AppProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedRefreshToken issue(AppUser user, String deviceName) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.auth().refreshTokenTtl());
        String value = SecretTokens.newValue();
        repository.save(new RefreshToken(user.getStoreId(), user.getId(), hash(value), truncate(deviceName), now,
                expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    /**
     * Valida o token e o revoga: cada refresh token vale uma vez só (rotação). Se um token já trocado
     * aparecer de novo depois da janela de tolerância, alguém pode tê-lo copiado, então todas as sessões
     * da pessoa caem. Dentro da janela é corrida benigna (duas abas renovando juntas, ou a resposta
     * anterior perdida na rede) e a renovação segue normalmente.
     */
    public RefreshToken consume(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCredentialsException(SESSION_EXPIRED);
        }
        Instant now = Instant.now(clock);
        RefreshToken token = repository.findByTokenHash(hash(value))
                .orElseThrow(() -> new InvalidCredentialsException(SESSION_EXPIRED));
        if (token.isRevoked()) {
            if (token.wasRotatedWithin(properties.auth().refreshReuseGrace(), now)) {
                return token;
            }
            repository.revokeAllActiveByUserId(token.getUserId(), now, RevokeReason.REVOKED);
            throw new InvalidCredentialsException(SESSION_EXPIRED);
        }
        if (token.isExpiredAt(now)) {
            throw new InvalidCredentialsException(SESSION_EXPIRED);
        }
        token.revoke(now, RevokeReason.ROTATED);
        return token;
    }

    public void revoke(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        repository.findByTokenHash(hash(value)).ifPresent(token -> token.revoke(now, RevokeReason.LOGOUT));
    }

    public void revokeAllForUser(UUID userId) {
        repository.revokeAllActiveByUserId(userId, Instant.now(clock), RevokeReason.REVOKED);
    }

    static String hash(String value) {
        return SecretTokens.sha256(value);
    }

    private static String truncate(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        String trimmed = deviceName.trim();
        return trimmed.length() <= MAX_DEVICE_NAME ? trimmed : trimmed.substring(0, MAX_DEVICE_NAME);
    }
}
