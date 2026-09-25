package com.pedeai.store.dto;

import com.pedeai.store.domain.Store;

import java.util.UUID;

public record StoreSummaryResponse(UUID id, String name) {
    public static StoreSummaryResponse from(Store store) {
        return new StoreSummaryResponse(store.getId(), store.getName());
    }
}
