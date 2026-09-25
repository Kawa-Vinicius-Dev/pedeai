package com.pedeai.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Preço calculado de um item montado. É o mesmo cálculo que o pedido vai usar. */
public record PriceQuoteResponse(
        UUID productId,
        String name,
        @Schema(types = {"string", "null"}) String code,
        @Schema(types = {"string", "null"}) UUID sectorId,
        int quantity,
        long basePriceCents,
        long optionsPriceCents,
        long unitPriceCents,
        long totalCents,
        List<QuotedOptionResponse> options
) {
    public record QuotedOptionResponse(
            UUID optionId,
            UUID groupId,
            String groupName,
            String name,
            @Schema(types = {"string", "null"}) String code,
            int quantity,
            long unitPriceCents
    ) {
    }
}
