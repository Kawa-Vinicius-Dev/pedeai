package com.pedeai.integration.dto;

import com.pedeai.order.domain.OrderSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ligar a loja a um merchant do iFood. O merchant precisa ter dado permissão ao PedeAí no Portal do Parceiro. */
public record ConnectionRequest(
        /** IFOOD (padrão), NINETY_NINE_FOOD ou OPEN_DELIVERY. */
        OrderSource provider,

        @NotBlank(message = "Informe o id da loja na plataforma (merchant).")
        @Size(max = 80, message = "O id do merchant pode ter até 80 caracteres.")
        String externalMerchantId,

        @NotNull(message = "Informe se o pedido é aceito automaticamente.")
        Boolean autoConfirm
) {
}
