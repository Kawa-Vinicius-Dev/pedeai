package com.pedeai.catalog.domain;

import java.util.List;
import java.util.UUID;

/** Dados de um produto vindos do formulário, já validados pelo serviço. */
public record ProductDraft(
        UUID categoryId,
        String code,
        String name,
        String description,
        long priceCents,
        UUID sectorId,
        List<UUID> optionGroupIds,
        boolean available,
        boolean active
) {
    public ProductDraft {
        optionGroupIds = List.copyOf(optionGroupIds);
    }
}
