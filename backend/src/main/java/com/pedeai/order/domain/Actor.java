package com.pedeai.order.domain;

import java.util.UUID;

/** Quem mudou o pedido, para a linha do tempo. */
public record Actor(ActorType type, UUID id, String name) {
    public static Actor user(UUID id, String name) {
        return new Actor(ActorType.USER, id, name);
    }

    /** "iFood" ou "99Food" na linha do tempo. */
    public static Actor marketplace(OrderSource source) {
        return new Actor(ActorType.MARKETPLACE, null, source == OrderSource.IFOOD ? "iFood" : "99Food");
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null, null);
    }
}
