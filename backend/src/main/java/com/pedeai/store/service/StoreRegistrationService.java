package com.pedeai.store.service;

import com.pedeai.shared.config.AppProperties;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.exception.TooManyRequestsException;
import com.pedeai.shared.security.AttemptLimiter;
import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.Slugs;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.RegisterStoreRequest;
import com.pedeai.store.event.StoreRegistered;
import com.pedeai.store.repository.AppUserRepository;
import com.pedeai.store.repository.StoreRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Cria uma loja nova com o dono dela e já abre a sessão do dono. */
@Service
public class StoreRegistrationService {
    static final String SIGNUP_DISABLED = "O cadastro de novas lojas está desativado.";
    static final String TOO_MANY_SIGNUPS = "Muitos cadastros em pouco tempo. Tente de novo mais tarde.";
    /**
     * Contra robô criando loja em massa: por IP (app.signups-per-address-per-hour, 5 por padrão) e, como o IP dá
     * para forjar, um teto geral de 40 vezes isso.
     */
    static final int OVERALL_FACTOR = 40;
    static final Duration SIGNUP_WINDOW = Duration.ofHours(1);

    private final StoreRepository storeRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AttemptLimiter byAddress;
    private final AttemptLimiter overall;

    public StoreRegistrationService(StoreRepository storeRepository, AppUserRepository userRepository,
                                    PasswordEncoder passwordEncoder, AuthService authService,
                                    AppProperties properties, ApplicationEventPublisher events, Clock clock) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        int perAddress = properties.signupsPerAddressPerHour();
        this.byAddress = new AttemptLimiter(perAddress, SIGNUP_WINDOW, clock);
        this.overall = new AttemptLimiter(perAddress * OVERALL_FACTOR, SIGNUP_WINDOW, clock);
    }

    /** {@code clientAddress}: o IP de quem cadastra, para o limite contra cadastro em massa. */
    @Transactional
    public AuthResult register(RegisterStoreRequest request, String deviceName, String clientAddress) {
        if (!properties.signupEnabled()) {
            throw new ForbiddenOperationException(SIGNUP_DISABLED);
        }
        if (byAddress.blocked(clientAddress) || overall.blocked("all")) {
            throw new TooManyRequestsException(TOO_MANY_SIGNUPS);
        }
        byAddress.failed(clientAddress);
        overall.failed("all");
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(Emails.ALREADY_IN_USE);
        }
        Instant now = Instant.now(clock);
        String name = request.storeName().trim();
        Store store = storeRepository.save(new Store(name, availableSlug(Slugs.from(name)), now));
        AppUser owner = userRepository.save(new AppUser(store.getId(), request.ownerName().trim(), email,
                passwordEncoder.encode(request.password()), Role.OWNER, now));
        // Na mesma transação: os cadastros padrão da loja nascem junto com ela.
        events.publishEvent(new StoreRegistered(store.getId()));
        return authService.startSession(owner, store, deviceName);
    }

    /** "pizzaria-bella", ou "pizzaria-bella-2" se já existir outra loja com o mesmo nome. */
    private String availableSlug(String base) {
        String slug = base;
        for (int suffix = 2; storeRepository.existsBySlug(slug); suffix++) {
            slug = base + "-" + suffix;
        }
        return slug;
    }
}
