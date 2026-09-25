package com.pedeai.order.domain;

import java.util.EnumSet;
import java.util.Set;

/** Na ordem do ciclo de vida: o status só anda para frente (ver docs/01-fluxos.md). */
public enum OrderStatus {
    RECEIVED, CONFIRMED, IN_PREPARATION, READY, DISPATCHED, COMPLETED, CANCELLED;

    /** Pedidos que ainda estão no quadro. */
    public static final Set<OrderStatus> ACTIVE = EnumSet.of(RECEIVED, CONFIRMED, IN_PREPARATION, READY, DISPATCHED);

    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Depois de começar o preparo, cancelar já tem custo para a loja. */
    public boolean isPreparationStarted() {
        return this == IN_PREPARATION || this == READY || this == DISPATCHED || this == COMPLETED;
    }
}
