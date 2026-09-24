package com.pedeai.store.service;

import com.pedeai.shared.config.AppProperties;
import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.RefreshToken;
import com.pedeai.store.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Emite, valida e revoga refresh tokens. Roda dentro da transação de quem chama. */
@Service
public class RefreshTokenService {
    static final String SESSION_EXPIRED = "Sua sessão expirou. Faça login novamente.";
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_DEVICE_NAME = 200;

    private final RefreshTokenRepository repository;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedRefreshToken issue(AppUser user, String deviceName) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.auth().refreshTokenTtl());
        String value = newTokenValue();
        repository.save(new RefreshToken(user.getStoreId(), user.getId(), hash(value), truncate(deviceName), now,
                expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    /**
     * Valida o token e o revoga: cada refresh token vale uma vez só (rotação). Se um token já trocado
     * aparecer de novo, alguém pode tê-lo copiado, então todas as sessões da pessoa caem.
     */
    public RefreshToken consume(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCredentialsException(SESSION_EXPIRED);
        }
        Instant now = Instant.now(clock);
        RefreshToken token = repository.findByTokenHash(hash(value))
                .orElseThrow(() -> new InvalidCredentialsException(SESSION_EXPIRED));
        if (token.isRevoked()) {
            repository.revokeAllActiveByUserId(token.getUserId(), now);
            throw new InvalidCredentialsException(SESSION_EXPIRED);
        }
        if (token.isExpiredAt(now)) {
            throw new InvalidCredentialsException(SESSION_EXPIRED);
        }
        token.revoke(now);
        return token;
    }

    public void revoke(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        repository.findByTokenHash(hash(value)).ifPresent(token -> token.revoke(now));
    }

    public void revokeAllForUser(UUID userId) {
        repository.revokeAllActiveByUserId(userId, Instant.now(clock));
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private String newTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String truncate(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        String trimmed = deviceName.trim();
        return trimmed.length() <= MAX_DEVICE_NAME ? trimmed : trimmed.substring(0, MAX_DEVICE_NAME);
    }
}
