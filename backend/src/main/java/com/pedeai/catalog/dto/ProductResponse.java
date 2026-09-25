package com.pedeai.catalog.dto;

import com.pedeai.catalog.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * {@code sectorId} é o setor escolhido no próprio produto. {@code effectiveSectorId} é para onde o
 * item vai de fato: produto, senão categoria, senão o setor padrão da loja.
 */
public record ProductResponse(
        UUID id,
        UUID categoryId,
        @Schema(types = {"string", "null"}) String code,
        String name,
        @Schema(types = {"string", "null"}) String description,
        long priceCents,
        @Schema(types = {"string", "null"}) UUID sectorId,
        @Schema(types = {"string", "null"}) UUID effectiveSectorId,
        List<UUID> optionGroupIds,
        boolean available,
        boolean active
) {
    public static ProductResponse from(Product product, UUID effectiveSectorId) {
        return new ProductResponse(product.getId(), product.getCategoryId(), product.getCode(), product.getName(),
                product.getDescription(), product.getPriceCents(), product.getSectorId(), effectiveSectorId,
                List.copyOf(product.getOptionGroupIds()), product.isAvailable(), product.isActive());
    }
}
