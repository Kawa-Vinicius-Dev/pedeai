package com.pedeai.store.service;

import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.CreateUserRequest;
import com.pedeai.store.dto.MeResponse;
import com.pedeai.store.dto.UpdateUserRequest;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.repository.AppUserRepository;
import com.pedeai.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserService service;
    private AppUser owner;
    private AppUser cashier;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, storeRepository, refreshTokenService, passwordEncoder, CLOCK);
        owner = new AppUser(STORE_ID, "Ana", "ana@example.com", "hash", Role.OWNER, NOW);
        cashier = new AppUser(STORE_ID, "Caio", "caio@example.com", "hash", Role.CASHIER, NOW);
    }

    @Test
    void listReturnsTheStoreTeam() {
        when(userRepository.findAllByStoreIdOrderByNameAsc(STORE_ID)).thenReturn(List.of(owner, cashier));

        List<UserResponse> users = service.list(STORE_ID);

        assertThat(users).extracting(UserResponse::name).containsExactly("Ana", "Caio");
    }

    @Test
    void getDoesNotFindUsersOfAnotherStore() {
        when(userRepository.findByIdAndStoreId(cashier.getId(), STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(STORE_ID, cashier.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(UserService.USER_NOT_FOUND);
    }

    @Test
    void createNormalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmail("bia@example.com")).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).then(returnsFirstArg());

        UserResponse created = service.create(STORE_ID,
                new CreateUserRequest(" Bia ", " Bia@Example.com ", "senha-da-bia", Role.WAITER));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(saved.capture());
        assertThat(created.email()).isEqualTo("bia@example.com");
        assertThat(created.name()).isEqualTo("Bia");
        assertThat(created.role()).isEqualTo(Role.WAITER);
        assertThat(saved.getValue().getStoreId()).isEqualTo(STORE_ID);
        assertThat(passwordEncoder.matches("senha-da-bia", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createRejectsEmailAlreadyInUse() {
        when(userRepository.existsByEmail("caio@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(STORE_ID,
                new CreateUserRequest("Caio", "caio@example.com", "senha-do-caio", Role.CASHIER)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRenamesAndChangesRole() {
        when(userRepository.findByIdAndStoreId(cashier.getId(), STORE_ID)).thenReturn(Optional.of(cashier));

        UserResponse updated = service.update(STORE_ID, cashier.getId(),
                new UpdateUserRequest(" Caio Lima ", Role.MANAGER, null, null));

        assertThat(updated.name()).isEqualTo("Caio Lima");
        assertThat(updated.role()).isEqualTo(Role.MANAGER);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void updateRefusesToDemoteTheLastOwner() {
        when(userRepository.findByIdAndStoreId(owner.getId(), STORE_ID)).thenReturn(Optional.of(owner));
        when(storeRepository.findByIdForUpdate(STORE_ID)).thenReturn(Optional.of(new Store("Loja", NOW)));
        when(userRepository.countByStoreIdAndRoleAndActiveTrue(STORE_ID, Role.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(STORE_ID, owner.getId(),
                new UpdateUserRequest(null, Role.MANAGER, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(UserService.LAST_OWNER);
        assertThat(owner.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    void updateRefusesToDeactivateTheLastOwner() {
        when(userRepository.findByIdAndStoreId(owner.getId(), STORE_ID)).thenReturn(Optional.of(owner));
        when(storeRepository.findByIdForUpdate(STORE_ID)).thenReturn(Optional.of(new Store("Loja", NOW)));
        when(userRepository.countByStoreIdAndRoleAndActiveTrue(STORE_ID, Role.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(STORE_ID, owner.getId(),
                new UpdateUserRequest(null, null, false, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(owner.isActive()).isTrue();
    }

    @Test
    void updateAllowsDemotingAnOwnerWhenAnotherOwnerExists() {
        when(userRepository.findByIdAndStoreId(owner.getId(), STORE_ID)).thenReturn(Optional.of(owner));
        when(storeRepository.findByIdForUpdate(STORE_ID)).thenReturn(Optional.of(new Store("Loja", NOW)));
        when(userRepository.countByStoreIdAndRoleAndActiveTrue(STORE_ID, Role.OWNER)).thenReturn(2L);

        UserResponse updated = service.update(STORE_ID, owner.getId(),
                new UpdateUserRequest(null, Role.MANAGER, null, null));

        assertThat(updated.role()).isEqualTo(Role.MANAGER);
    }

    @Test
    void deactivatingAUserRevokesTheirSessions() {
        when(userRepository.findByIdAndStoreId(cashier.getId(), STORE_ID)).thenReturn(Optional.of(cashier));

        UserResponse updated = service.update(STORE_ID, cashier.getId(),
                new UpdateUserRequest(null, null, false, null));

        assertThat(updated.active()).isFalse();
        verify(refreshTokenService).revokeAllForUser(cashier.getId());
    }

    @Test
    void resettingPasswordRevokesSessions() {
        when(userRepository.findByIdAndStoreId(cashier.getId(), STORE_ID)).thenReturn(Optional.of(cashier));

        service.update(STORE_ID, cashier.getId(), new UpdateUserRequest(null, null, null, "nova-senha-123"));

        assertThat(passwordEncoder.matches("nova-senha-123", cashier.getPasswordHash())).isTrue();
        verify(refreshTokenService).revokeAllForUser(cashier.getId());
    }

    @Test
    void meReturnsUserAndStore() {
        Store store = new Store("Pizzaria Bella", NOW);
        when(userRepository.findByIdAndStoreId(owner.getId(), STORE_ID)).thenReturn(Optional.of(owner));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        MeResponse me = service.me(STORE_ID, owner.getId());

        assertThat(me.user().name()).isEqualTo("Ana");
        assertThat(me.store().name()).isEqualTo("Pizzaria Bella");
    }
}
