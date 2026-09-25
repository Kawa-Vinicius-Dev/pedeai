package com.pedeai.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Com telefone, o cliente fica gravado na loja para o próximo pedido. Só com nome, fica só no pedido. */
public record OrderCustomerRequest(
        @NotBlank(message = "Informe o nome do cliente.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String name,

        @Size(max = 30, message = "Telefone inválido.")
        String phone
) {
}
