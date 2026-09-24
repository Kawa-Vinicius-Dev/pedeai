package com.pedeai.store.dto;

import com.pedeai.store.domain.Store;

import java.time.LocalTime;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        String name,
        String document,
        String phone,
        String timezone,
        LocalTime businessDayCutoff,
        int serviceFeeBp,
        boolean autoConfirmOwnOrders,
        boolean startPreparationOnConfirm
) {
    public static StoreResponse from(Store store) {
        return new StoreResponse(store.getId(), store.getName(), store.getDocument(), store.getPhone(),
                store.getTimezone(), store.getBusinessDayCutoff(), store.getServiceFeeBp(),
                store.isAutoConfirmOwnOrders(), store.isStartPreparationOnConfirm());
    }
}
