package com.pedeai.integration.dto;

import com.pedeai.integration.domain.MarketplaceConnection;
import jakarta.validation.constraints.NotNull;

/** {@code PAUSED}: para de receber pedidos por este vínculo, sem desfazê-lo. */
public record ConnectionUpdateRequest(
        @NotNull(message = "Informe a situação.")
        MarketplaceConnection.Status status,

        @NotNull(message = "Informe se o pedido é aceito automaticamente.")
        Boolean autoConfirm,

        /** O cardápio do app vem do PedeAí. Nulo: não muda. */
        Boolean catalogSync
) {
}
