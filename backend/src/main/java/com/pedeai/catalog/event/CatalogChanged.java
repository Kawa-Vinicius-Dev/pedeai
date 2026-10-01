package com.pedeai.catalog.event;

import java.util.Set;
import java.util.UUID;

/**
 * Parte do cardápio mudou: produtos, um grupo de adicionais (vale para os produtos que o usam) ou uma categoria (vale
 * para os produtos dela). Quem copia o cardápio para fora (iFood) escuta este evento, na mesma transação, sem o
 * catálogo saber quem é.
 */
public record CatalogChanged(UUID storeId, Set<UUID> productIds, UUID optionGroupId, UUID categoryId) {
    public CatalogChanged(UUID storeId, Set<UUID> productIds) {
        this(storeId, productIds, null, null);
    }

    public static CatalogChanged group(UUID storeId, UUID groupId) {
        return new CatalogChanged(storeId, Set.of(), groupId, null);
    }

    public static CatalogChanged category(UUID storeId, UUID categoryId) {
        return new CatalogChanged(storeId, Set.of(), null, categoryId);
    }
}
