package com.pedeai.store.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterStoreRequest(
        @NotBlank(message = "Informe o nome da loja.")
        @Size(max = 120, message = "O nome da loja pode ter até 120 caracteres.")
        String storeName,

        @NotBlank(message = "Informe o seu nome.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String ownerName,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "E-mail inválido.")
        @Size(max = 254, message = "E-mail muito longo.")
        String email,

        @NotBlank(message = "Informe a senha.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String password
) {
}
