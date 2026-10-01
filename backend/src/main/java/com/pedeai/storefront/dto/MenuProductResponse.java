package com.pedeai.storefront.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** {@code available = false}: pausado agora (acabou), aparece como esgotado. */
public record MenuProductResponse(
        UUID id,
        String name,
        @Schema(types = {"string", "null"}) String description,
        long priceCents,
        List<UUID> optionGroupIds,
        boolean available
) {
}
