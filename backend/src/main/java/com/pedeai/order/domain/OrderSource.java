package com.pedeai.order.domain;

/** Quem trouxe o pedido: a equipe da loja, o cliente pelo cardápio digital, ou um marketplace. */
public enum OrderSource {
    PEDEAI, DIGITAL_MENU, IFOOD, NINETY_NINE_FOOD;

    /** iFood e 99Food: o status e o cancelamento passam pela plataforma, e o pagamento online é dela. */
    public boolean isMarketplace() {
        return this == IFOOD || this == NINETY_NINE_FOOD;
    }
}
