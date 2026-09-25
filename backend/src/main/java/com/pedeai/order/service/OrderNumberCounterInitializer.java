package com.pedeai.order.service;

import com.pedeai.order.repository.OrderNumberCounterRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cria o contador do dia numa transação separada. Se dois pedidos tentarem criar ao mesmo tempo, só um
 * consegue, e a falha do outro não derruba a transação do pedido dele.
 */
@Component
class OrderNumberCounterInitializer {
    private final OrderNumberCounterRepository repository;

    OrderNumberCounterInitializer(OrderNumberCounterRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createCounter(UUID storeId, LocalDate businessDate) {
        repository.insertZero(storeId, businessDate);
    }
}
