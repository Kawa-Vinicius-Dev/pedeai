package com.pedeai.order.domain;

/**
 * Quem trouxe o pedido: a equipe da loja, o cliente pelo cardápio digital, um sistema de terceiros pela API de pedidos,
 * ou um marketplace.
 */
public enum OrderSource {
    PEDEAI, DIGITAL_MENU, API, IFOOD, NINETY_NINE_FOOD, OPEN_DELIVERY;

    /**
     * iFood, 99Food e outro app Open Delivery: o status e o cancelamento passam pela plataforma, e o pagamento online é
     * dela.
     */
    public boolean isMarketplace() {
        return this == IFOOD || this == NINETY_NINE_FOOD || this == OPEN_DELIVERY;
    }
}
