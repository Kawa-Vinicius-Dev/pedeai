package com.pedeai.store.service;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.dto.UpdateStoreRequest;
import com.pedeai.store.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class StoreService {
    static final String STORE_NOT_FOUND = "Loja não encontrada.";
    static final String SLUG_TAKEN = "Este endereço de cardápio já está em uso por outra loja.";

    private final StoreRepository storeRepository;
    private final Clock clock;

    public StoreService(StoreRepository storeRepository, Clock clock) {
        this.storeRepository = storeRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StoreResponse get(UUID storeId) {
        return StoreResponse.from(find(storeId));
    }

    /** A loja pelo endereço do cardápio digital. */
    @Transactional(readOnly = true)
    public StoreResponse getBySlug(String slug) {
        return storeRepository.findBySlug(slug).map(StoreResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(STORE_NOT_FOUND));
    }

    /** Abre ou fecha o cardápio digital para pedidos. */
    @Transactional
    public StoreResponse changeMenuOpen(UUID storeId, boolean open) {
        Store store = find(storeId);
        store.changeMenuOpen(open, Instant.now(clock));
        return StoreResponse.from(store);
    }

    @Transactional
    public StoreResponse update(UUID storeId, UpdateStoreRequest request) {
        Store store = find(storeId);
        if (request.slug() != null && !request.slug().equals(store.getSlug())) {
            if (storeRepository.existsBySlugAndIdNot(request.slug(), storeId)) {
                throw new ConflictException(SLUG_TAKEN);
            }
            store.changeSlug(request.slug(), Instant.now(clock));
        }
        store.update(
                request.name() == null ? store.getName() : request.name().trim(),
                request.document() == null ? store.getDocument() : blankToNull(request.document()),
                request.phone() == null ? store.getPhone() : blankToNull(request.phone()),
                request.timezone() == null ? store.getTimezone() : validTimeZone(request.timezone()),
                request.businessDayCutoff() == null ? store.getBusinessDayCutoff() : request.businessDayCutoff(),
                request.serviceFeeBp() == null ? store.getServiceFeeBp() : request.serviceFeeBp(),
                request.autoConfirmOwnOrders() == null
                        ? store.isAutoConfirmOwnOrders() : request.autoConfirmOwnOrders(),
                request.startPreparationOnConfirm() == null
                        ? store.isStartPreparationOnConfirm() : request.startPreparationOnConfirm(),
                Instant.now(clock));
        return StoreResponse.from(store);
    }

    private Store find(UUID storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new ResourceNotFoundException(STORE_NOT_FOUND));
    }

    private static String validTimeZone(String timezone) {
        String trimmed = timezone.trim();
        try {
            ZoneId.of(trimmed);
            return trimmed;
        } catch (DateTimeException exception) {
            throw new BusinessRuleException("Fuso horário inválido: " + trimmed + ".");
        }
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value.trim();
    }
}
