package com.pedeai.payment.service;

import com.pedeai.payment.domain.DefaultPaymentMethods;
import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.dto.PaymentMethodRequest;
import com.pedeai.payment.dto.PaymentMethodResponse;
import com.pedeai.payment.repository.PaymentMethodRepository;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.store.event.StoreRegistered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentMethodService {
    static final String NOT_FOUND = "Forma de pagamento não encontrada.";
    static final String DUPLICATE_NAME = "Já existe uma forma de pagamento com esse nome.";

    private final PaymentMethodRepository repository;
    private final Clock clock;

    public PaymentMethodService(PaymentMethodRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list(UUID storeId) {
        return repository.findAllByStoreIdOrderBySortOrderAscNameAsc(storeId).stream()
                .map(PaymentMethodResponse::from)
                .toList();
    }

    @Transactional
    public PaymentMethodResponse create(UUID storeId, PaymentMethodRequest request) {
        String name = request.name().trim();
        if (repository.existsByStoreIdAndNameIgnoreCase(storeId, name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        Instant now = Instant.now(clock);
        PaymentMethod method = new PaymentMethod(storeId, name, request.type(), repository.findMaxSortOrder(storeId) + 1,
                now);
        if (!request.active()) {
            method.update(name, request.type(), false, now);
        }
        return PaymentMethodResponse.from(repository.save(method));
    }

    @Transactional
    public PaymentMethodResponse update(UUID storeId, UUID id, PaymentMethodRequest request) {
        PaymentMethod method = repository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        String name = request.name().trim();
        if (repository.existsByStoreIdAndNameIgnoreCaseAndIdNot(storeId, name, id)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        method.update(name, request.type(), request.active(), Instant.now(clock));
        return PaymentMethodResponse.from(method);
    }

    /** Loja nova já nasce com Dinheiro, Pix, cartões e vale. Na mesma transação do cadastro da loja. */
    @EventListener
    public void onStoreRegistered(StoreRegistered event) {
        createDefaults(event.storeId());
    }

    void createDefaults(UUID storeId) {
        if (repository.existsByStoreId(storeId)) {
            return;
        }
        Instant now = Instant.now(clock);
        List<DefaultPaymentMethods.Entry> defaults = DefaultPaymentMethods.ALL;
        for (int position = 0; position < defaults.size(); position++) {
            DefaultPaymentMethods.Entry entry = defaults.get(position);
            repository.save(new PaymentMethod(storeId, entry.name(), entry.type(), position, now));
        }
    }
}
