package com.pedeai.store.service;

import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.Role;
import com.pedeai.store.domain.AppUser;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.CreateUserRequest;
import com.pedeai.store.dto.MeResponse;
import com.pedeai.store.dto.StoreSummaryResponse;
import com.pedeai.store.dto.UpdateUserRequest;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.repository.AppUserRepository;
import com.pedeai.store.repository.StoreRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    static final String USER_NOT_FOUND = "Usuário não encontrado.";
    static final String LAST_OWNER = "A loja precisa de pelo menos um dono ativo.";

    private final AppUserRepository userRepository;
    private final StoreRepository storeRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(AppUserRepository userRepository, StoreRepository storeRepository,
                       RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(UUID storeId) {
        return userRepository.findAllByStoreIdOrderByNameAsc(storeId).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID storeId, UUID userId) {
        return UserResponse.from(find(storeId, userId));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID storeId, UUID userId) {
        AppUser user = find(storeId, userId);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException(StoreService.STORE_NOT_FOUND));
        return new MeResponse(UserResponse.from(user), StoreSummaryResponse.from(store));
    }

    @Transactional
    public UserResponse create(UUID storeId, CreateUserRequest request) {
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(Emails.ALREADY_IN_USE);
        }
        AppUser user = new AppUser(storeId, request.name().trim(), email,
                passwordEncoder.encode(request.password()), request.role(), Instant.now(clock));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID storeId, UUID userId, UpdateUserRequest request) {
        AppUser user = find(storeId, userId);
        if (losesOwnerAccess(user, request)) {
            ensureAnotherActiveOwner(storeId);
        }

        Instant now = Instant.now(clock);
        boolean revokeSessions = false;
        if (request.name() != null) {
            user.rename(request.name().trim(), now);
        }
        if (request.role() != null) {
            user.changeRole(request.role(), now);
        }
        if (request.active() != null && request.active() != user.isActive()) {
            user.changeActive(request.active(), now);
            revokeSessions = !request.active();
        }
        if (request.password() != null) {
            user.changePassword(passwordEncoder.encode(request.password()), now);
            revokeSessions = true;
        }
        if (revokeSessions) {
            refreshTokenService.revokeAllForUser(user.getId());
        }
        return UserResponse.from(user);
    }

    private static boolean losesOwnerAccess(AppUser user, UpdateUserRequest request) {
        boolean isActiveOwner = user.getRole() == Role.OWNER && user.isActive();
        boolean demoted = request.role() != null && request.role() != Role.OWNER;
        boolean deactivated = Boolean.FALSE.equals(request.active());
        return isActiveOwner && (demoted || deactivated);
    }

    private void ensureAnotherActiveOwner(UUID storeId) {
        // Trava a loja para dois donos não se rebaixarem ao mesmo tempo e deixarem a loja sem dono.
        storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ResourceNotFoundException(StoreService.STORE_NOT_FOUND));
        if (userRepository.countByStoreIdAndRoleAndActiveTrue(storeId, Role.OWNER) <= 1) {
            throw new ConflictException(LAST_OWNER);
        }
    }

    private AppUser find(UUID storeId, UUID userId) {
        return userRepository.findByIdAndStoreId(userId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    }
}
