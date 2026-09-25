package com.pedeai.order.dto;

import com.pedeai.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code version} é a versão do pedido que a tela mostrava. Se outra tela mudou o pedido antes, a resposta é
 * 409 e a tela recarrega.
 */
public record ChangeOrderStatusRequest(
        @NotNull(message = "Informe o novo status.")
        OrderStatus status,

        @Size(max = 300, message = "O motivo pode ter até 300 caracteres.")
        String reason,

        Long version
) {
}
