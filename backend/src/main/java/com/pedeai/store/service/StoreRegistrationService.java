package com.pedeai.store.service;

import com.pedeai.shared.config.AppProperties;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
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
import java.time.Instant;

/** Cria uma loja nova com o dono dela e já abre a sessão do dono. */
@Service
public class StoreRegistrationService {
    static final String SIGNUP_DISABLED = "O cadastro de novas lojas está desativado.";

    private final StoreRepository storeRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

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
    }

    @Transactional
    public AuthResult register(RegisterStoreRequest request, String deviceName) {
        if (!properties.signupEnabled()) {
            throw new ForbiddenOperationException(SIGNUP_DISABLED);
        }
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(Emails.ALREADY_IN_USE);
        }
        Instant now = Instant.now(clock);
        Store store = storeRepository.save(new Store(request.storeName().trim(), now));
        AppUser owner = userRepository.save(new AppUser(store.getId(), request.ownerName().trim(), email,
                passwordEncoder.encode(request.password()), Role.OWNER, now));
        // Na mesma transação: os cadastros padrão da loja nascem junto com ela.
        events.publishEvent(new StoreRegistered(store.getId()));
        return authService.startSession(owner, store, deviceName);
    }
}
