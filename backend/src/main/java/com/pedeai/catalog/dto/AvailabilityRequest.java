package com.pedeai.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull(message = "Informe se está disponível.") Boolean available) {
}
