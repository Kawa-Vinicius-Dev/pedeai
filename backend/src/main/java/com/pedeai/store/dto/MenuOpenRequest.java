package com.pedeai.store.dto;

import jakarta.validation.constraints.NotNull;

public record MenuOpenRequest(
        @NotNull(message = "Informe se o cardápio está aberto.")
        Boolean open
) {
}
