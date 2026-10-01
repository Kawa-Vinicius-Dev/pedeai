package com.pedeai.order.domain;

import java.util.UUID;

/** Quem mudou o pedido, para a linha do tempo. */
public record Actor(ActorType type, UUID id, String name) {
    public static Actor user(UUID id, String name) {
        return new Actor(ActorType.USER, id, name);
    }

    /** "iFood", "99Food" ou "Open Delivery" na linha do tempo. */
    public static Actor marketplace(OrderSource source) {
        String name = switch (source) {
            case IFOOD -> "iFood";
            case NINETY_NINE_FOOD -> "99Food";
            default -> "Open Delivery";
        };
        return new Actor(ActorType.MARKETPLACE, null, name);
    }

    /** O próprio cliente, no cardápio digital. */
    public static Actor customer(String name) {
        return new Actor(ActorType.CUSTOMER, null, name);
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null, null);
    }
}
