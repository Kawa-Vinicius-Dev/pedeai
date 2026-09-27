package com.pedeai.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ligar a loja a um merchant do iFood. O merchant precisa ter dado permissão ao PedeAí no Portal do Parceiro. */
public record ConnectionRequest(
        @NotBlank(message = "Informe o merchant do iFood.")
        @Size(max = 80, message = "O id do merchant pode ter até 80 caracteres.")
        String externalMerchantId,

        @NotNull(message = "Informe se o pedido é aceito automaticamente.")
        Boolean autoConfirm
) {
}
