package com.pedeai.order.service;

import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.LoyaltyStatusResponse;
import com.pedeai.order.repository.OrderRepository;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Fidelidade simples: a cada N pedidos concluídos, o cliente ganha um desconto fixo no próximo. Nada é guardado no
 * cliente: a conta sai dos pedidos. Pedido cancelado não conta, e cancelar o pedido do prêmio devolve o prêmio.
 * O pedido que usou o prêmio não conta para o próximo.
 */
@Service
public class LoyaltyService {
    private final OrderRepository orderRepository;
    private final StoreService storeService;

    public LoyaltyService(OrderRepository orderRepository, StoreService storeService) {
        this.orderRepository = orderRepository;
        this.storeService = storeService;
    }

    @Transactional(readOnly = true)
    public LoyaltyStatusResponse status(UUID storeId, UUID customerId) {
        return status(storeService.get(storeId), customerId);
    }

    LoyaltyStatusResponse status(StoreResponse store, UUID customerId) {
        if (store.loyaltyOrders() == null || customerId == null) {
            return LoyaltyStatusResponse.disabled();
        }
        int every = store.loyaltyOrders();
        long completed = orderRepository.countByStoreIdAndCustomerIdAndStatusAndLoyaltyReward(store.id(), customerId,
                OrderStatus.COMPLETED, false);
        long used = orderRepository.countByStoreIdAndCustomerIdAndStatusNotAndLoyaltyReward(store.id(), customerId,
                OrderStatus.CANCELLED, true);
        boolean available = completed / every > used;
        int toNext = available ? 0 : (int) (every - completed % every);
        return new LoyaltyStatusResponse(true, completed, toNext, available,
                available ? store.loyaltyDiscountCents() : 0);
    }
}
