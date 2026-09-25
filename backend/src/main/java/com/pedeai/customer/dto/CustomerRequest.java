package com.pedeai.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "Informe o nome do cliente.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String name,

        @NotBlank(message = "Informe o telefone.")
        @Size(max = 30, message = "Telefone inválido.")
        String phone,

        @Email(message = "E-mail inválido.")
        @Size(max = 254, message = "O e-mail pode ter até 254 caracteres.")
        String email,

        @Size(max = 500, message = "As observações podem ter até 500 caracteres.")
        String notes
) {
}
