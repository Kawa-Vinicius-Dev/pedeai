package com.pedeai.store.service;

import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.RegisterStoreRequest;
import com.pedeai.store.repository.AppUserRepository;
import com.pedeai.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreRegistrationServiceTest {
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private AuthService authService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final RegisterStoreRequest request =
            new RegisterStoreRequest(" Pizzaria Bella ", " Ana ", "Ana@Example.com", "senha-forte-1");

    @Test
    void createsStoreWithOwnerAndOpensSession() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(storeRepository.save(any(Store.class))).then(returnsFirstArg());
        when(userRepository.save(any(AppUser.class))).then(returnsFirstArg());

        service(true).register(request, "Chrome");

        ArgumentCaptor<AppUser> owner = ArgumentCaptor.forClass(AppUser.class);
        ArgumentCaptor<Store> store = ArgumentCaptor.forClass(Store.class);
        verify(authService).startSession(owner.capture(), store.capture(), eq("Chrome"));
        assertThat(store.getValue().getName()).isEqualTo("Pizzaria Bella");
        assertThat(owner.getValue().getRole()).isEqualTo(Role.OWNER);
        assertThat(owner.getValue().getName()).isEqualTo("Ana");
        assertThat(owner.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(owner.getValue().getStoreId()).isEqualTo(store.getValue().getId());
        assertThat(passwordEncoder.matches("senha-forte-1", owner.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void rejectsEmailAlreadyInUse() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service(true).register(request, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(Emails.ALREADY_IN_USE);
        verify(storeRepository, never()).save(any());
    }

    @Test
    void rejectsWhenSignupIsDisabled() {
        assertThatThrownBy(() -> service(false).register(request, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(StoreRegistrationService.SIGNUP_DISABLED);
        verify(storeRepository, never()).save(any());
    }

    private StoreRegistrationService service(boolean signupEnabled) {
        return new StoreRegistrationService(storeRepository, userRepository, passwordEncoder, authService,
                properties(signupEnabled), CLOCK);
    }
}
