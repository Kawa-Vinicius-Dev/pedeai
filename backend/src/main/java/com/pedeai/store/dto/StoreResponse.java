package com.pedeai.store.dto;

import com.pedeai.store.domain.Store;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        String name,
        @Schema(types = {"string", "null"}) String document,
        @Schema(types = {"string", "null"}) String phone,
        String timezone,
        LocalTime businessDayCutoff,
        int serviceFeeBp,
        boolean autoConfirmOwnOrders,
        boolean startPreparationOnConfirm,
        String slug,
        boolean menuOpen,
        boolean menuAutoConfirm,
        List<OpeningHoursResponse> openingHours
) {
    public static StoreResponse from(Store store) {
        return new StoreResponse(store.getId(), store.getName(), store.getDocument(), store.getPhone(),
                store.getTimezone(), store.getBusinessDayCutoff(), store.getServiceFeeBp(),
                store.isAutoConfirmOwnOrders(), store.isStartPreparationOnConfirm(), store.getSlug(),
                store.isMenuOpen(), store.isMenuAutoConfirm(),
                store.getOpeningHours().stream().map(OpeningHoursResponse::from).toList());
    }
}
