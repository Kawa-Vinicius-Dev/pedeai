package com.pedeai.store.service;

import com.pedeai.shared.exception.BusinessRuleException;
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

    @Transactional
    public StoreResponse update(UUID storeId, UpdateStoreRequest request) {
        Store store = find(storeId);
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
