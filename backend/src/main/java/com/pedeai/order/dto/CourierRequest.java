package com.pedeai.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourierRequest(
        @NotBlank(message = "Informe o nome do entregador.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String name,

        @Schema(types = {"string", "null"})
        @Size(max = 30, message = "Telefone inválido.")
        String phone,

        @NotNull(message = "Informe se o entregador está ativo.")
        Boolean active
) {
}
