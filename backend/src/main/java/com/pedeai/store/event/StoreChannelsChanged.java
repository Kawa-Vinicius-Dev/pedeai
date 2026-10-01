package com.pedeai.store.event;

import java.util.UUID;

/**
 * Algo da loja que os apps de pedido também precisam saber: a chave "Cardápio aberto" ({@code status}), o horário
 * ({@code hours}) ou o acréscimo de preço do iFood ({@code prices}).
 */
public record StoreChannelsChanged(UUID storeId, boolean status, boolean hours, boolean prices) {
}
