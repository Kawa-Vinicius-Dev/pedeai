package com.pedeai.storefront.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code name}: para a loja lembrar quem usa a chave ("Site da loja", "Bot do WhatsApp"). */
public record ApiKeyRequest(
        @NotBlank(message = "Dê um nome para a chave (ex.: Site da loja).")
        @Size(max = 60, message = "O nome pode ter até 60 caracteres.")
        String name
) {
}
