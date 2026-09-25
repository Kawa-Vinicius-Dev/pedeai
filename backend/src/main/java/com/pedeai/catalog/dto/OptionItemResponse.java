package com.pedeai.catalog.dto;

import com.pedeai.catalog.domain.OptionItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record OptionItemResponse(
        UUID id,
        @Schema(types = {"string", "null"}) String code,
        String name,
        long priceCents,
        boolean available,
        boolean active
) {
    public static OptionItemResponse from(OptionItem option) {
        return new OptionItemResponse(option.getId(), option.getCode(), option.getName(), option.getPriceCents(),
                option.isAvailable(), option.isActive());
    }
}
