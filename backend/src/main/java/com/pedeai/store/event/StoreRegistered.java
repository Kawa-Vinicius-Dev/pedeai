package com.pedeai.store.event;

import java.util.UUID;

/** Loja nova criada. Outros módulos criam os cadastros padrão dela (formas de pagamento, por exemplo). */
public record StoreRegistered(UUID storeId) {
}
