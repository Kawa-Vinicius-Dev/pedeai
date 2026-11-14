package com.pedeai.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** {@code quantity} nulo: o produto deixa de controlar estoque. */
public record StockRequest(
        @Schema(types = {"integer", "null"})
        @Min(value = 0, message = "O estoque não pode ser negativo.")
        @Max(value = 100_000, message = "Estoque alto demais.")
        Integer quantity
) {
}
