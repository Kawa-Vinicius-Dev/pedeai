package com.pedeai.catalog.dto;

import com.pedeai.catalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        @Schema(types = {"string", "null"}) UUID defaultSectorId,
        int sortOrder,
        boolean active
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDefaultSectorId(),
                category.getSortOrder(), category.isActive());
    }
}
