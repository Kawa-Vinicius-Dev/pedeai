package com.pedeai.storefront.service;

import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.storefront.domain.ApiKey;
import com.pedeai.storefront.dto.ApiKeyRequest;
import com.pedeai.storefront.dto.ApiKeyResponse;
import com.pedeai.storefront.dto.CreatedApiKeyResponse;
import com.pedeai.storefront.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Chaves da API de pedidos (docs/05-integracoes.md#api-de-pedidos-do-pedeaí): 256 bits aleatórios com o prefixo
 * {@code pk_}. O banco guarda o SHA-256; quem tem acesso ao banco não consegue usar a chave.
 */
@Service
public class ApiKeyService {
    static final String PREFIX = "pk_";
    static final String INVALID = "Chave de API inválida ou revogada.";
    static final String NOT_FOUND = "Chave de API não encontrada.";
    /** O "último uso" é para a loja ver se a chave ainda é usada: não precisa de uma escrita por requisição. */
    private static final Duration LAST_USED_PRECISION = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository repository;
    private final Clock clock;

    public ApiKeyService(ApiKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(UUID storeId) {
        return repository.findAllByStoreIdOrderByCreatedAtDesc(storeId).stream().map(ApiKeyResponse::from).toList();
    }

    @Transactional
    public CreatedApiKeyResponse create(UUID storeId, ApiKeyRequest request) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiKey key = repository.save(new ApiKey(storeId, request.name().trim(), secret.substring(0, 10), hash(secret),
                Instant.now(clock)));
        return new CreatedApiKeyResponse(ApiKeyResponse.from(key), secret);
    }

    @Transactional
    public void revoke(UUID storeId, UUID id) {
        repository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND))
                .revoke(Instant.now(clock));
    }

    /** A loja dona da chave. Chave ausente, errada ou revogada: 401. */
    @Transactional
    public UUID authenticate(String secret) {
        if (secret == null || !secret.startsWith(PREFIX) || secret.length() > 100) {
            throw new InvalidCredentialsException(INVALID);
        }
        ApiKey key = repository.findByKeyHash(hash(secret)).filter(ApiKey::isActive)
                .orElseThrow(() -> new InvalidCredentialsException(INVALID));
        Instant now = Instant.now(clock);
        if (key.getLastUsedAt() == null || key.getLastUsedAt().plus(LAST_USED_PRECISION).isBefore(now)) {
            key.used(now);
        }
        return key.getStoreId();
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
