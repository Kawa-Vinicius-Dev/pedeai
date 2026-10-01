package com.pedeai.storefront.dto;

/** A chave inteira, só nesta resposta: quem cria copia e guarda no sistema que vai usar. */
public record CreatedApiKeyResponse(ApiKeyResponse key, String secret) {
}
