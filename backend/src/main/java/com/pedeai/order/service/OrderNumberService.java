package com.pedeai.order.service;

import com.pedeai.order.repository.OrderNumberCounterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Número curto do pedido ("Pedido 42"), que recomeça a cada dia operacional. O {@code UPDATE} trava a linha do
 * contador até o fim da transação do pedido, então dois pedidos simultâneos nunca pegam o mesmo número.
 */
@Service
public class OrderNumberService {
    private final OrderNumberCounterRepository repository;
    private final OrderNumberCounterInitializer initializer;

    public OrderNumberService(OrderNumberCounterRepository repository, OrderNumberCounterInitializer initializer) {
        this.repository = repository;
        this.initializer = initializer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(UUID storeId, LocalDate businessDate) {
        if (repository.increment(storeId, businessDate) == 0) {
            try {
                initializer.createCounter(storeId, businessDate);
            } catch (DataIntegrityViolationException alreadyCreated) {
                // Outro pedido do mesmo dia criou o contador ao mesmo tempo. Segue com o dele.
            }
            repository.increment(storeId, businessDate);
        }
        return repository.current(storeId, businessDate);
    }
}
