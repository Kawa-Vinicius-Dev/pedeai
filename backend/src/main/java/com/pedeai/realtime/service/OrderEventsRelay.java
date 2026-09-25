package com.pedeai.realtime.service;

import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.event.OrderStatusChanged;
import com.pedeai.realtime.dto.RealtimeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Avisa as telas depois do commit: se a transação desfizer, ninguém é avisado de um pedido que não existe. */
@Component
class OrderEventsRelay {
    private final RealtimeBroadcaster broadcaster;

    OrderEventsRelay(RealtimeBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onOrderCreated(OrderCreated event) {
        broadcaster.publish(event.storeId(), new RealtimeEvent("order.created", event.orderId(), event.number(),
                event.status().name(), event.version()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onOrderStatusChanged(OrderStatusChanged event) {
        broadcaster.publish(event.storeId(), new RealtimeEvent("order.status_changed", event.orderId(),
                event.number(), event.to().name(), event.version()));
    }
}
